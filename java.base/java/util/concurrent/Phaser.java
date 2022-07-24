/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * 用Phaser替代CyclicBarrier和CountDownLatch
 * 从JDK7开始，新增了一个同步工具类Phaser，其功能比CyclicBarrier和CountDownLatch更加强大。
 * 1.用Phaser替代CountDownLatch
 * 考虑讲CountDownLatch时的例子，1个主线程要等10个worker线程完成之后，才能做接下来的事情，也可以用Phaser来实现此功能。在CountDownLatch中，
 * 主要是2个方法：await()和countDown()，在Phaser中，与之相对应的方法是awaitAdance(int n)和arrive()。
 *
 * 2.用Phaser替代CyclicBarrier考虑前面讲CyclicBarrier时，10个工程师去公司应聘的例子，也可以用Phaser实现，代码基本类似。
 * arriveAndAwaitAdance()就是 arrive()与 awaitAdvance(int)的组合，表示“我自己已到达这个同步点，同时要等待所有人都到达这个同步点，然后再一起前行”。
 *
 * Phaser新特性
 * 特性1：动态调整线程个数
 * CyclicBarrier 所要同步的线程个数是在构造方法中指定的，之后不能更改，而 Phaser 可以在运行期间动态地调整要同步的线程个数。
 * Phaser 提供了下面这些方法来增加、减少所要同步的线程个数。
 * 特性2：层次Phaser
 * 多个Phaser可以组成如下图所示的树状结构，可以通过在构造方法中传入父Phaser来实现。
 *
 * 大致了解了Phaser的用法和新特性之后，下面仔细剖析其实现原理。Phaser没有基于AQS来实现，
 * 但具备AQS的核心特性：state变量、CAS操作、阻塞队列。先从state变量说起。
 * @author liuzhen
 * @date 2022/4/16 18:26
 * @return
 */
public class Phaser {
    private static final int MAX_PARTIES = 0xffff;
    private static final int MAX_PHASE = Integer.MAX_VALUE;
    private static final int PARTIES_SHIFT = 16;
    private static final int PHASE_SHIFT = 32;
    private static final int UNARRIVED_MASK = 0xffff;      // to mask ints
    private static final long PARTIES_MASK = 0xffff0000L; // to mask longs
    private static final long COUNTS_MASK = 0xffffffffL;
    private static final long TERMINATION_BIT = 1L << 63;

    // some special values
    private static final int ONE_ARRIVAL = 1;
    private static final int ONE_PARTY = 1 << PARTIES_SHIFT;
    private static final int ONE_DEREGISTER = ONE_ARRIVAL | ONE_PARTY;
    private static final int EMPTY = 1;

    /**
     * 这个64位的state变量被拆成4部分，下图为state变量各部分：
     * 1: 是否完成同步。最高位0表示未同步完成，1表示同步完成，初始最高位为0。
     * 31: 轮数
     * 16: 总线程数
     * 16: 未到达线程数
     */
    private volatile long state;

    private final Phaser parent;

    private final Phaser root;

    /** 两个引用表示链表的头部，可以避免线程冲突。（链表的头部指针） */
    private final AtomicReference<QNode> evenQ;
    private final AtomicReference<QNode> oddQ;

    /**
     * 在这里，阻塞使用的是一个称为Treiber Stack的数据结构，而不是AQS的双向链表。Treiber Stack是一个无锁的栈，
     * 它是一个单向链表，出栈、入栈都在链表头部，所以只需要一个head指针，而不需要tail指针，
     * @date 2022/7/24 0:41
     */
    static final class QNode implements ForkJoinPool.ManagedBlocker {
        final Phaser phaser;
        final int phase;
        final boolean interruptible;
        final boolean timed;
        boolean wasInterrupted;
        long nanos;
        final long deadline;
        /** 每个Node节点对应一个线程 */
        volatile Thread thread; // nulled to cancel wait
        /** 下一个节点的引用 */
        QNode next;

        QNode(Phaser phaser, int phase, boolean interruptible, boolean timed, long nanos) {
            this.phaser = phaser;
            this.phase = phase;
            this.interruptible = interruptible;
            this.nanos = nanos;
            this.timed = timed;
            this.deadline = timed ? System.nanoTime() + nanos : 0L;
            thread = Thread.currentThread();
        }

        public boolean isReleasable() {
            if (thread == null)
                return true;
            if (phaser.getPhase() != phase) {
                thread = null;
                return true;
            }
            if (Thread.interrupted())
                wasInterrupted = true;
            if (wasInterrupted && interruptible) {
                thread = null;
                return true;
            }
            if (timed && (nanos <= 0L || (nanos = deadline - System.nanoTime()) <= 0L)) {
                thread = null;
                return true;
            }
            return false;
        }

        public boolean block() {
            while (!isReleasable()) {
                if (timed)
                    LockSupport.parkNanos(this, nanos);
                else
                    LockSupport.park(this);
            }
            return true;
        }
    }

    public Phaser() {
        this(null, 0);
    }

    public Phaser(int parties) {
        this(null, parties);
    }

    public Phaser(Phaser parent) {
        this(parent, 0);
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/16 18:54
     * @param parent
     * @param parties
     * @return
     */
    public Phaser(Phaser parent, int parties) {
        // 如果parties数超出了最大个数（2的16次方），抛异常
        if (parties >>> PARTIES_SHIFT != 0)
            throw new IllegalArgumentException("Illegal number of parties");

        // 初始化轮数为0
        int phase = 0;
        this.parent = parent;
        if (parent != null) {
            final Phaser root = parent.root;
            // 父节点的根节点就是自己的根节点
            this.root = root;
            // 父节点的evenQ就是自己的evenQ
            this.evenQ = root.evenQ;
            // 父节点的oddQ就是自己的oddQ
            this.oddQ = root.oddQ;
            // 如果参与者不是0，则向父节点注册自己
            if (parties != 0)
                phase = parent.doRegister(1);
        } else {
            // 如果父节点为null，则自己就是root节点
            this.root = this;
            // 创建奇数节点
            this.evenQ = new AtomicReference<QNode>();
            // 创建偶数节点
            this.oddQ = new AtomicReference<QNode>();
        }
        // 位或操作，赋值state。最高位 为0，表示同步未完成
        // 当parties=0时，state被赋予一个EMPTY常量，常量为1； 当parties != 0时，把phase值左移32位；把parties左移16位；
        // 然后parties也作为最低的16位，3个值做或操作，赋值给state。
        this.state = (parties == 0) ? (long)EMPTY : ((long)phase << PHASE_SHIFT) | ((long)parties << PARTIES_SHIFT) | ((long)parties);
    }

    /**
     * 注册一个
     * @author liuzhen
     * @date 2022/4/16 18:38
     * @param
     * @return int
     */
    public int register() {
        return doRegister(1);
    }

    /**
     * 注册多个
     * @author liuzhen
     * @date 2022/4/16 18:39
     * @param parties
     * @return int
     */
    public int bulkRegister(int parties) {
        if (parties < 0)
            throw new IllegalArgumentException();
        if (parties == 0)
            return getPhase();
        return doRegister(parties);
    }

    /**
     * arrive()和 arriveAndDeregister()内部调用的都是 doArrive(boolean)方法。
     * 区别在于前者只是把“未达到线程数”减1；后者则把“未到达线程数”和“下一轮的总线程数”都减1。
     * @date 2022/7/24 0:29
     * @param
     * @return int
     */
    public int arrive() {
        return doArrive(ONE_ARRIVAL);
    }

    /**
     *
     * @date 2022/7/24 0:29
     * @param phase
     * @return int
     */
    public int awaitAdvance(int phase) {
        final Phaser root = this.root;
        // 当只有一个Phaser，没有树状结构时，root就是this
        long s = (root == this) ? state : reconcileState();
        int p = (int)(s >>> PHASE_SHIFT);
        // phase已经结束，无需阻塞，直接返回。
        if (phase < 0)
            return phase;
        if (p == phase)
            // 阻塞在phase这一轮上
            return root.internalAwaitAdvance(phase, null);
        return p;
    }

    /**
     * 解除注册
     * @author liuzhen
     * @date 2022/4/16 18:39
     * @param
     * @return int
     */
    public int arriveAndDeregister() {
        return doArrive(ONE_DEREGISTER);
    }

    /**
     * arrive()与 awaitAdvance(int)的组合
     * 表示“我自己已到达这个同步点，同时要等待所有人都到达这个同步点，然后再一起前行”。
     * @date 2022/7/24 0:28
     * @param
     * @return int
     */
    public int arriveAndAwaitAdvance() {
        // Specialization of doArrive+awaitAdvance eliminating some reads/paths
        final Phaser root = this.root;
        for (; ; ) {
            long s = (root == this) ? state : reconcileState();
            int phase = (int)(s >>> PHASE_SHIFT);
            if (phase < 0)
                return phase;
            int counts = (int)s;
            int unarrived = (counts == EMPTY) ? 0 : (counts & UNARRIVED_MASK);
            if (unarrived <= 0)
                throw new IllegalStateException(badArrive(s));
            if (STATE.compareAndSet(this, s, s -= ONE_ARRIVAL)) {
                if (unarrived > 1)
                    return root.internalAwaitAdvance(phase, null);
                if (root != this)
                    return parent.arriveAndAwaitAdvance();
                long n = s & PARTIES_MASK;  // base of next state
                int nextUnarrived = (int)n >>> PARTIES_SHIFT;
                if (onAdvance(phase, nextUnarrived))
                    n |= TERMINATION_BIT;
                else if (nextUnarrived == 0)
                    n |= EMPTY;
                else
                    n |= nextUnarrived;
                int nextPhase = (phase + 1) & MAX_PHASE;
                n |= (long)nextPhase << PHASE_SHIFT;
                if (!STATE.compareAndSet(this, s, n))
                    return (int)(state >>> PHASE_SHIFT); // terminated
                releaseWaiters(phase);
                return nextPhase;
            }
        }
    }

    public int awaitAdvanceInterruptibly(int phase) throws InterruptedException {
        final Phaser root = this.root;
        long s = (root == this) ? state : reconcileState();
        int p = (int)(s >>> PHASE_SHIFT);
        if (phase < 0)
            return phase;
        if (p == phase) {
            QNode node = new QNode(this, phase, true, false, 0L);
            p = root.internalAwaitAdvance(phase, node);
            if (node.wasInterrupted)
                throw new InterruptedException();
        }
        return p;
    }

    public int awaitAdvanceInterruptibly(int phase, long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        long nanos = unit.toNanos(timeout);
        final Phaser root = this.root;
        long s = (root == this) ? state : reconcileState();
        int p = (int)(s >>> PHASE_SHIFT);
        if (phase < 0)
            return phase;
        if (p == phase) {
            QNode node = new QNode(this, phase, true, true, nanos);
            p = root.internalAwaitAdvance(phase, node);
            if (node.wasInterrupted)
                throw new InterruptedException();
            else if (p == phase)
                throw new TimeoutException();
        }
        return p;
    }

    public void forceTermination() {
        // Only need to change root state
        final Phaser root = this.root;
        long s;
        while ((s = root.state) >= 0) {
            if (STATE.compareAndSet(root, s, s | TERMINATION_BIT)) {
                // signal all threads
                releaseWaiters(0); // Waiters on evenQ
                releaseWaiters(1); // Waiters on oddQ
                return;
            }
        }
    }

    /**
     * 获取当前的轮数。当前轮数同步完成，返回值是一个负数（最高位为1）
     * @author liuzhen
     * @date 2022/4/16 18:43
     * @param
     * @return int
     */
    public final int getPhase() {
        // 当前phase未完成，返回值是一个负数（最高位为1）
        return (int)(root.state >>> PHASE_SHIFT);
    }

    /**
     * 获取总注册线程数
     * @author liuzhen
     * @date 2022/4/16 18:46
     * @param
     * @return int
     */
    public int getRegisteredParties() {
        return partiesOf(state);
    }

    public int getArrivedParties() {
        return arrivedOf(reconcileState());
    }

    /**
     * 获取位到达的线程数
     * @author liuzhen
     * @date 2022/4/16 18:48
     * @param
     * @return int
     */
    public int getUnarrivedParties() {
        return unarrivedOf(reconcileState());
    }

    public Phaser getParent() {
        return parent;
    }

    public Phaser getRoot() {
        return root;
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/16 18:45
     * @param
     * @return boolean
     */
    public boolean isTerminated() {
        // 当前轮数同步完成，最高位为1
        return root.state < 0L;
    }

    protected boolean onAdvance(int phase, int registeredParties) {
        return registeredParties == 0;
    }

    public String toString() {
        return stateToString(reconcileState());
    }

    private String stateToString(long s) {
        return super.toString() + "[phase = " + phaseOf(s) + " parties = " + partiesOf(s) + " arrived = " + arrivedOf(s) + "]";
    }

    // The following unpacking methods are usually manually inlined

    private static int unarrivedOf(long s) {
        int counts = (int)s;
        // 不等于空，则截取低16位
        return (counts == EMPTY) ? 0 : (counts & UNARRIVED_MASK);
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/16 18:48
     * @param s
     * @return int
     */
    private static int partiesOf(long s) {
        // 先把state转为32位int，再右移16位
        return (int)s >>> PARTIES_SHIFT;
    }

    private static int phaseOf(long s) {
        return (int)(s >>> PHASE_SHIFT);
    }

    private static int arrivedOf(long s) {
        int counts = (int)s;
        return (counts == EMPTY) ? 0 : (counts >>> PARTIES_SHIFT) - (counts & UNARRIVED_MASK);
    }

    private String badArrive(long s) {
        return "Attempted arrival of unregistered party for " + stateToString(s);
    }

    private String badRegister(long s) {
        return "Attempt to register more than " + MAX_PARTIES + " parties for " + stateToString(s);
    }

    /**
     * 关于下面的方法，有以下几点说明：
     * 1. 定义了2个常量如下。
     *  当 deregister=false 时，只最低的16位需要减 1，adj=ONE_ARRIVAL；
     *  当deregister=true时，低32位中的2个16位都需要减1，adj=ONE_ARRIVAL|ONE_PARTY。
     * 2. 把未到达线程数减1。减了之后，如果还未到0，什么都不做，直接返回。如果到0，会做2件事情：
     *  1. 重置state，把state的未到达线程个数重置到总的注册的线程数中，同时phase加 1；
     *  2. 唤醒队列中的线程。
     * @date 2022/7/24 0:47
     * @param adjust
     * @return int
     */
    private int doArrive(int adjust) {
        final Phaser root = this.root;
        for (; ; ) {
            long s = (root == this) ? state : reconcileState();
            int phase = (int)(s >>> PHASE_SHIFT);
            if (phase < 0)
                return phase;
            int counts = (int)s;
            int unarrived = (counts == EMPTY) ? 0 : (counts & UNARRIVED_MASK);
            if (unarrived <= 0)
                throw new IllegalStateException(badArrive(s));
            if (STATE.compareAndSet(this, s, s -= adjust)) {
                if (unarrived == 1) {
                    long n = s & PARTIES_MASK;  // base of next state
                    int nextUnarrived = (int)n >>> PARTIES_SHIFT;
                    if (root == this) {
                        if (onAdvance(phase, nextUnarrived))
                            n |= TERMINATION_BIT;
                        else if (nextUnarrived == 0)
                            n |= EMPTY;
                        else
                            n |= nextUnarrived;
                        int nextPhase = (phase + 1) & MAX_PHASE;
                        n |= (long)nextPhase << PHASE_SHIFT;
                        STATE.compareAndSet(this, s, n);
                        releaseWaiters(phase);
                    } else if (nextUnarrived == 0) { // propagate deregistration
                        phase = parent.doArrive(ONE_DEREGISTER);
                        STATE.compareAndSet(this, s, s | EMPTY);
                    } else
                        phase = parent.doArrive(ONE_ARRIVAL);
                }
                return phase;
            }
        }
    }

    private int doRegister(int registrations) {
        // adjustment to state
        long adjust = ((long)registrations << PARTIES_SHIFT) | registrations;
        final Phaser parent = this.parent;
        int phase;
        for (; ; ) {
            long s = (parent == null) ? state : reconcileState();
            int counts = (int)s;
            int parties = counts >>> PARTIES_SHIFT;
            int unarrived = counts & UNARRIVED_MASK;
            if (registrations > MAX_PARTIES - parties)
                throw new IllegalStateException(badRegister(s));
            phase = (int)(s >>> PHASE_SHIFT);
            if (phase < 0)
                break;
            if (counts != EMPTY) {                  // not 1st registration
                if (parent == null || reconcileState() == s) {
                    if (unarrived == 0)             // wait out advance
                        root.internalAwaitAdvance(phase, null);
                    else if (STATE.compareAndSet(this, s, s + adjust))
                        break;
                }
            } else if (parent == null) {              // 1st root registration
                long next = ((long)phase << PHASE_SHIFT) | adjust;
                if (STATE.compareAndSet(this, s, next))
                    break;
            } else {
                synchronized (this) {               // 1st sub registration
                    if (state == s) {               // recheck under lock
                        phase = parent.doRegister(1);
                        if (phase < 0)
                            break;
                        // finish registration whenever parent registration
                        // succeeded, even when racing with termination,
                        // since these are part of the same "transaction".
                        while (!STATE.weakCompareAndSet(this, s, ((long)phase << PHASE_SHIFT) | adjust)) {
                            s = state;
                            phase = (int)(root.state >>> PHASE_SHIFT);
                            // assert (int)s == EMPTY;
                        }
                        break;
                    }
                }
            }
        }
        return phase;
    }

    private long reconcileState() {
        final Phaser root = this.root;
        long s = state;
        if (root != this) {
            int phase, p;
            // CAS to root phase with current parties, tripping unarrived
            while ((phase = (int)(root.state >>> PHASE_SHIFT)) != (int)(s >>> PHASE_SHIFT) && !STATE.weakCompareAndSet(this, s, s = (
                ((long)phase << PHASE_SHIFT) |
                ((phase < 0) ? (s & COUNTS_MASK) : (((p = (int)s >>> PARTIES_SHIFT) == 0) ? EMPTY : ((s & PARTIES_MASK) | p))))))
                s = state;
        }
        return s;
    }

    // Waiting mechanics

    /**
     * 为了减少并发冲突，这里定义了2个链表，也就是2个Treiber Stack。当phase为奇数轮的时候，
     * 阻塞线程放在oddQ里面；当phase为偶数轮的时候，阻塞线程放在evenQ里面。
     * 遍历整个栈，只要栈当中节点的phase不等于当前Phaser的phase，说明该节点不是当前轮的，而是前一轮的，应该被释放并唤醒。
     * @date 2022/7/24 0:46
     * @param phase
     * @return void
     */
    private void releaseWaiters(int phase) {
        QNode q;   // first element of queue
        Thread t;  // its thread
        // 根据phase是奇数还是偶数，使用evenQ还是oddQ
        AtomicReference<QNode> head = (phase & 1) == 0 ? evenQ : oddQ;
        // 遍历栈
        while ((q = head.get()) != null && q.phase != (int)(root.state >>> PHASE_SHIFT)) {
            if (head.compareAndSet(q, q.next) && (t = q.thread) != null) {
                q.thread = null;
                LockSupport.unpark(t);
            }
        }
    }

    private int abortWait(int phase) {
        AtomicReference<QNode> head = (phase & 1) == 0 ? evenQ : oddQ;
        for (; ; ) {
            Thread t;
            QNode q = head.get();
            int p = (int)(root.state >>> PHASE_SHIFT);
            if (q == null || ((t = q.thread) != null && q.phase == p))
                return p;
            if (head.compareAndSet(q, q.next) && t != null) {
                q.thread = null;
                LockSupport.unpark(t);
            }
        }
    }

    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    static final int SPINS_PER_ARRIVAL = (NCPU < 2) ? 1 : 1 << 8;

    /**
     * 下面调用了ForkJoinPool.managedBlock(ManagedBlocker blocker)方法，目的是把node对应的线程阻塞。
     * ManagerdBlocker是ForkJoinPool里面的一个接口。
     * QNode实现了该接口，实现原理还是park()，如下所示。之所以没有直接使用park()/unpark()来实现阻塞、唤醒，
     * 而是封装了ManagedBlocker这一层，主要是出于使用上的方便考虑。一方面是park()可能被中断唤醒，另一方面是带超时时间的park()，把这二者都封装在一起。
     * @param phase
     * @param node
     * @return
     */
    private int internalAwaitAdvance(int phase, QNode node) {
        // assert root == this;
        releaseWaiters(phase - 1);          // ensure old queue clean
        boolean queued = false;           // true when node is enqueued
        int lastUnarrived = 0;            // to increase spins upon change
        int spins = SPINS_PER_ARRIVAL;
        long s;
        int p;

        // 不可中断模式的自旋
        while ((p = (int)((s = state) >>> PHASE_SHIFT)) == phase) {
            if (node == null) {           // spinning in noninterruptible mode
                int unarrived = (int)s & UNARRIVED_MASK;
                if (unarrived != lastUnarrived && (lastUnarrived = unarrived) < NCPU)
                    spins += SPINS_PER_ARRIVAL;
                boolean interrupted = Thread.interrupted();
                // 自旋结束，建一个节点，之后进入阻 塞
                if (interrupted || --spins < 0) { // need node to record intr
                    node = new QNode(this, phase, false, false, 0L);
                    node.wasInterrupted = interrupted;
                } else
                    Thread.onSpinWait();
            } else if (node.isReleasable()) // 从阻塞唤醒，退出while循环
                break;
            else if (!queued) {           // push onto queue
                AtomicReference<QNode> head = (phase & 1) == 0 ? evenQ : oddQ;
                QNode q = node.next = head.get();
                if ((q == null || q.phase == phase) && (int)(state >>> PHASE_SHIFT) == phase) // avoid stale enq
                    // 节点入栈
                    queued = head.compareAndSet(q, node);
            } else {
                try {
                    // 调用node.block()阻塞
                    ForkJoinPool.managedBlock(node);
                } catch (InterruptedException cantHappen) {
                    node.wasInterrupted = true;
                }
            }
        }

        if (node != null) {
            if (node.thread != null)
                node.thread = null;       // avoid need for unpark()
            if (node.wasInterrupted && !node.interruptible)
                Thread.currentThread().interrupt();
            if (p == phase && (p = (int)(state >>> PHASE_SHIFT)) == phase)
                return abortWait(phase); // possibly clean up on abort
        }
        releaseWaiters(phase);
        return p;
    }

    // VarHandle mechanics
    private static final VarHandle STATE;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            STATE = l.findVarHandle(Phaser.class, "state", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        // Reduce the risk of rare disastrous classloading in first call to
        // LockSupport.park: https://bugs.openjdk.java.net/browse/JDK-8074773
        Class<?> ensureLoaded = LockSupport.class;
    }
}
