/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.LockSupport;

/**
 * 使用场景：Exchanger用于线程之间交换数据，其使用代码很简单，是一个exchange(...)方法
 * 实现原理：Exchanger的核心机制和Lock一样，也是CAS+park/unpark。
 *
 * 每个线程在调用exchange(...)方法交换数据的时候，会先创建一个Node对象。
 * 这个Node对象就是对该线程的包装，里面包含了3个重要字段：第一个是该线程要交互的数据，第二个是对方线程交换来的数据，最后一个是该线程自身。
 * 一个Node只能支持2个线程之间交换数据，要实现多个线程并行地交换数据，需要多个Node，因此在Exchanger里面定义了Node数组： private volatile Node[] arena;
 * @author liuzhen
 * @date 2022/4/16 18:08
 * @return
 */
public class Exchanger<V> {

    private static final int ASHIFT = 5;

    private static final int MMASK = 0xff;

    private static final int SEQ = MMASK + 1;

    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    static final int FULL = (NCPU >= (MMASK << 1)) ? MMASK : NCPU >>> 1;

    private static final int SPINS = 1 << 10;

    private static final Object NULL_ITEM = new Object();

    private static final Object TIMED_OUT = new Object();

    /**
     * 添加了Contended注解，表示伪共享与缓存行填充
     */
    @jdk.internal.vm.annotation.Contended
    static final class Node {
        int index;              // Arena index
        int bound;              // Last recorded value of Exchanger.bound
        /** 本次绑定中，CAS操作失败次数 */
        int collides;           // Number of CAS failures at current bound
        /** 自旋伪随机 */
        int hash;               // Pseudo-random for spins
        /** 本线程要交换的数据 */
        Object item;            // This thread's current item
        /** 对方线程交换来的数据 */
        volatile Object match;  // Item provided by releasing thread
        /** 当前线程阻塞的时候设置该属性，不阻塞为null。 */
        volatile Thread parked; // Set to this thread when parked, else null
    }

    static final class Participant extends ThreadLocal<Node> {
        public Node initialValue() {
            return new Node();
        }
    }

    private final Participant participant;

    private volatile Node[] arena;

    private volatile Node slot;

    private volatile int bound;

    /** 
     * 当启用arenas的时候，使用该方法进行线程间的数据交换。
     * @author liuzhen
     * @date 2022/4/16 18:20 
     * @param item 本线程要交换的非null数据。
     * @param timed 如果需要计时等待，则设置为true。
     * @param ns 表示计时等待的最大时长。
     * @return 对方线程交换来的数据。如果线程被中断，或者等待超时，则返回null。
     */
    private final Object arenaExchange(Object item, boolean timed, long ns) {
        Node[] a = arena;
        int alen = a.length;
        Node p = participant.get();

        // 访问下标为i处的slot数据
        for (int i = p.index; ; ) {                      // access slot at i
            int b, m, c;
            int j = (i << ASHIFT) + ((1 << ASHIFT) - 1);
            if (j < 0 || j >= alen)
                j = alen - 1;

            // 取出arena数组的第j个Node元素
            Node q = (Node)AA.getAcquire(a, j);
            // 如果q不是null，则将数组的第j个元素由q设置为null
            if (q != null && AA.compareAndSet(a, j, q, null)) {
                // 获取对方线程交换来的数据
                Object v = q.item;                     // release
                // 设置本方线程交换的数据
                q.match = item;
                // 获取对方线程对象
                Thread w = q.parked;
                if (w != null)
                    // 如果对方线程非空，则唤醒对方线程
                    LockSupport.unpark(w);
                return v;
            } else if (i <= (m = (b = bound) & MMASK) && q == null) { // 如果自旋次数没达到边界，且q为null
                // 提供本方数据
                p.item = item;                         // offer
                // 将arena的第j个元素由null设置为p
                if (AA.compareAndSet(a, j, null, p)) {
                    long end = (timed && m == 0) ? System.nanoTime() + ns : 0L;
                    Thread t = Thread.currentThread(); // wait

                    // 自旋等待
                    for (int h = p.hash, spins = SPINS; ; ) {
                        // 获取对方交换来的数据
                        Object v = p.match;
                        // 如果对方交换来的数据非空
                        if (v != null) {
                            // 将p设置为null，CAS操作
                            MATCH.setRelease(p, null);
                            // 清空
                            p.item = null;             // clear for next use
                            p.hash = h;
                            // 返回交换来的数据
                            return v;
                        } else if (spins > 0) { // 产生随机数，用于限制自旋次数
                            h ^= h << 1;
                            h ^= h >>> 3;
                            h ^= h << 10; // xorshift
                            if (h == 0)                // initialize hash
                                h = SPINS | (int)t.getId();
                            else if (h < 0 &&          // approx 50% true
                                     (--spins & ((SPINS >>> 1) - 1)) == 0)
                                Thread.yield();        // two yields per wait
                        } else if (AA.getAcquire(a, j) != p) // 如果arena的第j个元素不是p
                            spins = SPINS;       // releaser hasn't set match yet
                        else if (!t.isInterrupted() && m == 0 && (!timed || (ns = end - System.nanoTime()) > 0L)) {
                            p.parked = t;              // minimize window
                            if (AA.getAcquire(a, j) == p) {
                                if (ns == 0L)
                                    // 当前线程阻塞，等待交换数据
                                    LockSupport.park(this);
                                else
                                    LockSupport.parkNanos(this, ns);
                            }
                            p.parked = null;
                        }
                        // arena的第j个元素是p并且CAS设置arena的第j个元素由p设置 为null成功
                        else if (AA.getAcquire(a, j) == p && AA.compareAndSet(a, j, p, null)) {
                            if (m != 0)                // try to shrink
                                BOUND.compareAndSet(this, b, b + SEQ - 1);
                            p.item = null;
                            p.hash = h;
                            i = p.index >>>= 1;        // descend
                            // 如果线程被中断，则返回null值
                            if (Thread.interrupted())
                                return null;
                            // 如果超时，返回TIMED_OUT。
                            if (timed && m == 0 && ns <= 0L)
                                return TIMED_OUT;
                            break;                     // expired; restart
                        }
                    }
                } else
                    p.item = null;                     // clear offer
            } else {
                if (p.bound != b) {                    // stale; reset
                    p.bound = b;
                    p.collides = 0;
                    i = (i != m || m == 0) ? m : m - 1;
                } else if ((c = p.collides) < m || m == FULL || !BOUND.compareAndSet(this, b, b + SEQ + 1)) {
                    p.collides = c + 1;
                    i = (i == 0) ? m : i - 1;          // cyclically traverse
                } else
                    i = m + 1;                         // grow
                p.index = i;
            }
        }
    }

    public Exchanger() {
        participant = new Participant();
    }

    /**
     * 方法中，如果arena不是null，表示启用了arena方式交换数据。如果arena不是null，并且线程被中断，则抛异常
     * 如果arena不是null，并且arenaExchange的返回值为null，则抛异常。对方线程交换来的null值是封装为NULL_ITEM对象的，而不是null。
     * 如果slotExchange的返回值是null，并且线程被中断，则抛异常。
     * 如果slotExchange的返回值是null，并且areaExchange的返回值是null，则抛异常。
     * @author liuzhen
     * @date 2022/4/16 18:12
     * @param x
     * @return V
     */
    @SuppressWarnings("unchecked")
    public V exchange(V x) throws InterruptedException {
        Object v;
        Node[] a;
        Object item = (x == null) ? NULL_ITEM : x; // translate null args
        if (((a = arena) != null || (v = slotExchange(item, false, 0L)) == null) && ((Thread.interrupted() || // disambiguates null return
                                                                                      (v = arenaExchange(item, false, 0L)) == null)))
            throw new InterruptedException();
        return (v == NULL_ITEM) ? null : (V)v;
    }

    @SuppressWarnings("unchecked")
    public V exchange(V x, long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        Object v;
        Object item = (x == null) ? NULL_ITEM : x;
        long ns = unit.toNanos(timeout);
        if ((arena != null || (v = slotExchange(item, true, ns)) == null) && ((Thread.interrupted() || (v = arenaExchange(item, true, ns)) == null)))
            throw new InterruptedException();
        if (v == TIMED_OUT)
            throw new TimeoutException();
        return (v == NULL_ITEM) ? null : (V)v;
    }

    /**
     * 如果不启用arenas，则使用该方法进行线程间数据交换。
     * 如果不启用arenas，则使用该方法进行线程间数据交换。
     * @author liuzhen
     * @date 2022/4/16 18:13
     * @param item 需要交换的数据
     * @param timed 是否是计时等待，true表示是计时等待
     * @param ns 如果是计时等待，该值表示最大等待的时长。
     * @return 对方线程交换来的数据；如果等待超时或线程中断，或者启用了arena，则返回null。
     * @return java.lang.Object
     */
    private final Object slotExchange(Object item, boolean timed, long ns) {
        // participant在初始化的时候设置初始值为new Node()
        // 获取本线程要交换的数据节点
        Node p = participant.get();
        // 获取当前线程
        Thread t = Thread.currentThread();
        // 如果线程被中断，则返回null。
        if (t.isInterrupted()) // preserve interrupt status so caller can recheck
            return null;

        for (Node q; ; ) {
            // 如果slot非空，表明有其他线程在等待该线程交换数据
            if ((q = slot) != null) {
                // CAS操作，将当前线程的slot由slot设置为null
                // 如果操作成功，则执行if中的语句
                if (SLOT.compareAndSet(this, q, null)) {
                    // 获取对方线程交换来的数据
                    Object v = q.item;
                    // 设置要交换的数据
                    q.match = item;
                    // 获取q中阻塞的线程对象
                    Thread w = q.parked;
                    if (w != null)
                        // 如果对方阻塞的线程非空，则唤醒阻塞的线程
                        LockSupport.unpark(w);
                    return v;
                }

                // create arena on contention, but continue until slot null
                // 创建arena用于处理多个线程需要交换数据的场合，防止slot冲突
                if (NCPU > 1 && bound == 0 && BOUND.compareAndSet(this, 0, SEQ))
                    arena = new Node[(FULL + 2) << ASHIFT];
            } else if (arena != null) // 如果arena不是null，需要调用者调用arenaExchange方法接着获取对方线程交 换来的数据
                return null; // caller must reroute to arenaExchange
            else {
                // 如果slot为null，表示对方没有线程等待该线程交换数据
                // 设置要交换的本方数据
                p.item = item;
                // 设置当前线程要交换的数据到slot
                // CAS操作，如果设置失败，则进入下一轮for循环
                if (SLOT.compareAndSet(this, null, p))
                    break;
                p.item = null;
            }
        }

        // await release
        // 没有对方线程等待交换数据，将当前线程要交换的数据放到slot中，是一个Node对象
        // 然后阻塞，等待唤醒
        int h = p.hash;
        // 如果是计时等待交换，则计算超时时间；否则设置为0。
        long end = timed ? System.nanoTime() + ns : 0L;
        // 如果CPU核心数大于1，则使用SPINS数，自旋；否则为1，没必要自旋。
        int spins = (NCPU > 1) ? SPINS : 1;
        // 记录对方线程交换来的数据
        Object v;
        // 如果p.match==null，表示还没有线程交换来数据
        while ((v = p.match) == null) {
            // 如果自旋次数大于0，计算hash随机数
            if (spins > 0) {
                // 生成随机数，用于自旋次数控制
                h ^= h << 1;
                h ^= h >>> 3;
                h ^= h << 10;
                if (h == 0)
                    h = SPINS | (int)t.getId();
                else if (h < 0 && (--spins & ((SPINS >>> 1) - 1)) == 0)
                    Thread.yield();
            } else if (slot != p) // p是ThreadLocal记录的当前线程的Node。 // 如果slot不是p表示slot是别的线程放进去的
                spins = SPINS;
            else if (!t.isInterrupted() && arena == null && (!timed || (ns = end - System.nanoTime()) > 0L)) {
                p.parked = t;
                if (slot == p) {
                    if (ns == 0L) {
                        // 阻塞当前线程
                        LockSupport.park(this);
                    }
                    else
                        // 如果是计时等待，则阻塞当前线程指定时间
                        LockSupport.parkNanos(this, ns);
                }
                p.parked = null;
            } else if (SLOT.compareAndSet(this, p, null)) { // 没有被中断但是超时了，返回TIMED_OUT，否则返回null
                v = timed && ns <= 0L && !t.isInterrupted() ? TIMED_OUT : null;
                break;
            }
        }

        // match设置为null值 CAS
        MATCH.setRelease(p, null);
        p.item = null;
        p.hash = h;
        // 返回获取的对方线程交换来的数据
        return v;
    }

    // VarHandle mechanics
    private static final VarHandle BOUND;
    private static final VarHandle SLOT;
    private static final VarHandle MATCH;
    private static final VarHandle AA;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            BOUND = l.findVarHandle(Exchanger.class, "bound", int.class);
            SLOT = l.findVarHandle(Exchanger.class, "slot", Node.class);
            MATCH = l.findVarHandle(Node.class, "match", Object.class);
            AA = MethodHandles.arrayElementVarHandle(Node[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

}
