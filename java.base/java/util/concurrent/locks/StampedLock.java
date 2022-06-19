/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */


package java.util.concurrent.locks;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;
import jdk.internal.vm.annotation.ReservedStackAccess;

/**
 *
 * @date 2022/6/19 22:58
 */
public class StampedLock implements java.io.Serializable {

    private static final long serialVersionUID = -6001602636862214147L;

    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    /** 自旋的次数，超过这个数字，进入阻塞。 */
    private static final int SPINS = (NCPU > 1) ? 1 << 6 : 1;

    private static final int HEAD_SPINS = (NCPU > 1) ? 1 << 10 : 1;

    private static final int MAX_HEAD_SPINS = (NCPU > 1) ? 1 << 16 : 1;

    private static final int OVERFLOW_YIELD_RATE = 7; // must be power 2 - 1

    private static final int LG_READERS = 7;

    // Values for lock state and stamp operations
    private static final long RUNIT = 1L;
    /** 第8位表示写锁 */
    private static final long WBIT = 1L << LG_READERS;
    /** 最低的7位表示读锁 */
    private static final long RBITS = WBIT - 1L;
    /** 读锁的数目 */
    private static final long RFULL = RBITS - 1L;
    /** 读锁和写锁状态合二为一 */
    private static final long ABITS = RBITS | WBIT;
    private static final long SBITS = ~RBITS; // note overlap with ABITS

    /** state的初始值 */
    private static final long ORIGIN = WBIT << 1;

    // Special value from cancelled acquire methods so caller can throw IE
    private static final long INTERRUPTED = 1L;

    // Values for node status; order matters
    private static final int WAITING = -1;
    private static final int CANCELLED = 1;

    // Modes for nodes (int not boolean to allow arithmetic)
    private static final int RMODE = 0;
    private static final int WMODE = 1;

    static final class WNode {
        volatile WNode prev;
        volatile WNode next;
        volatile WNode cowait; // list of linked readers
        volatile Thread thread; // non-null while possibly parked
        volatile int status; // 0, WAITING, or CANCELLED
        final int mode; // RMODE or WMODE

        WNode(int m, WNode p) {
            mode = m;
            prev = p;
        }
    }

    private transient volatile WNode whead;
    private transient volatile WNode wtail;

    // views
    transient ReadLockView readLockView;
    transient WriteLockView writeLockView;
    transient ReadWriteLockView readWriteLockView;

    private transient volatile long state;
    private transient int readerOverflow;

    public StampedLock() {
        state = ORIGIN;
    }

    private boolean casState(long expectedValue, long newValue) {
        return STATE.compareAndSet(this, expectedValue, newValue);
    }

    private long tryWriteLock(long s) {
        // assert (s & ABITS) == 0L;
        long next;
        if (casState(s, next = s | WBIT)) {
            VarHandle.storeStoreFence();
            return next;
        }
        return 0L;
    }

    /**
     * 加锁操作
     * 当state&ABITS==0的时候，说明既没有线程持有读锁，也没有线程持有写锁，此时当前线程才有资格通过CAS操作state。
     * 若操作不成功，则调用acquireWrite()方法进入阻塞队列，并进行自旋，这个方法是整个加锁操作的核心。
     * @date 2022/6/19 23:07
     * @param
     * @return long
     */
    @ReservedStackAccess
    public long writeLock() {
        long next;
        return ((next = tryWriteLock()) != 0L) ? next : acquireWrite(false, 0L);
    }

    @ReservedStackAccess
    public long tryWriteLock() {
        long s;
        return (((s = state) & ABITS) == 0L) ? tryWriteLock(s) : 0L;
    }

    public long tryWriteLock(long time, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(time);
        if (!Thread.interrupted()) {
            long next, deadline;
            if ((next = tryWriteLock()) != 0L)
                return next;
            if (nanos <= 0L)
                return 0L;
            if ((deadline = System.nanoTime() + nanos) == 0L)
                deadline = 1L;
            if ((next = acquireWrite(true, deadline)) != INTERRUPTED)
                return next;
        }
        throw new InterruptedException();
    }

    @ReservedStackAccess
    public long writeLockInterruptibly() throws InterruptedException {
        long next;
        if (!Thread.interrupted() && (next = acquireWrite(true, 0L)) != INTERRUPTED)
            return next;
        throw new InterruptedException();
    }

    @ReservedStackAccess
    public long readLock() {
        long s, next;
        // bypass acquireRead on common uncontended case
        return (whead == wtail && ((s = state) & ABITS) < RFULL && casState(s, next = s + RUNIT)) ? next
            : acquireRead(false, 0L);
    }

    @ReservedStackAccess
    public long tryReadLock() {
        long s, m, next;
        while ((m = (s = state) & ABITS) != WBIT) {
            if (m < RFULL) {
                if (casState(s, next = s + RUNIT))
                    return next;
            } else if ((next = tryIncReaderOverflow(s)) != 0L)
                return next;
        }
        return 0L;
    }

    @ReservedStackAccess
    public long tryReadLock(long time, TimeUnit unit) throws InterruptedException {
        long s, m, next, deadline;
        long nanos = unit.toNanos(time);
        if (!Thread.interrupted()) {
            if ((m = (s = state) & ABITS) != WBIT) {
                if (m < RFULL) {
                    if (casState(s, next = s + RUNIT))
                        return next;
                } else if ((next = tryIncReaderOverflow(s)) != 0L)
                    return next;
            }
            if (nanos <= 0L)
                return 0L;
            if ((deadline = System.nanoTime() + nanos) == 0L)
                deadline = 1L;
            if ((next = acquireRead(true, deadline)) != INTERRUPTED)
                return next;
        }
        throw new InterruptedException();
    }

    @ReservedStackAccess
    public long readLockInterruptibly() throws InterruptedException {
        long s, next;
        if (!Thread.interrupted()
            // bypass acquireRead on common uncontended case
            && ((whead == wtail && ((s = state) & ABITS) < RFULL && casState(s, next = s + RUNIT))
                || (next = acquireRead(true, 0L)) != INTERRUPTED))
            return next;
        throw new InterruptedException();
    }

    public long tryOptimisticRead() {
        long s;
        return (((s = state) & WBIT) == 0L) ? (s & SBITS) : 0L;
    }

    public boolean validate(long stamp) {
        VarHandle.acquireFence();
        return (stamp & SBITS) == (state & SBITS);
    }

    private static long unlockWriteState(long s) {
        return ((s += WBIT) == 0L) ? ORIGIN : s;
    }

    /** 
     *
     * @date 2022/6/19 23:15 
     * @param s 
     * @return long
     */
    private long unlockWriteInternal(long s) {
        long next;
        WNode h;
        STATE.setVolatile(this, next = unlockWriteState(s));
        if ((h = whead) != null && h.status != 0)
            release(h);
        return next;
    }

    /** 
     * 锁的释放操作
     * @date 2022/6/19 23:13
     * @param stamp 
     * @return void
     */
    @ReservedStackAccess
    public void unlockWrite(long stamp) {
        if (state != stamp || (stamp & WBIT) == 0L)
            throw new IllegalMonitorStateException();
        unlockWriteInternal(stamp);
    }

    @ReservedStackAccess
    public void unlockRead(long stamp) {
        long s, m;
        WNode h;
        while (((s = state) & SBITS) == (stamp & SBITS) && (stamp & RBITS) > 0L && ((m = s & RBITS) > 0L)) {
            if (m < RFULL) {
                if (casState(s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    return;
                }
            } else if (tryDecReaderOverflow(s) != 0L)
                return;
        }
        throw new IllegalMonitorStateException();
    }

    @ReservedStackAccess
    public void unlock(long stamp) {
        if ((stamp & WBIT) != 0L)
            unlockWrite(stamp);
        else
            unlockRead(stamp);
    }

    public long tryConvertToWriteLock(long stamp) {
        long a = stamp & ABITS, m, s, next;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L) {
                if (a != 0L)
                    break;
                if ((next = tryWriteLock(s)) != 0L)
                    return next;
            } else if (m == WBIT) {
                if (a != m)
                    break;
                return stamp;
            } else if (m == RUNIT && a != 0L) {
                if (casState(s, next = s - RUNIT + WBIT)) {
                    VarHandle.storeStoreFence();
                    return next;
                }
            } else
                break;
        }
        return 0L;
    }

    public long tryConvertToReadLock(long stamp) {
        long a, s, next;
        WNode h;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((a = stamp & ABITS) >= WBIT) {
                // write stamp
                if (s != stamp)
                    break;
                STATE.setVolatile(this, next = unlockWriteState(s) + RUNIT);
                if ((h = whead) != null && h.status != 0)
                    release(h);
                return next;
            } else if (a == 0L) {
                // optimistic read stamp
                if ((s & ABITS) < RFULL) {
                    if (casState(s, next = s + RUNIT))
                        return next;
                } else if ((next = tryIncReaderOverflow(s)) != 0L)
                    return next;
            } else {
                // already a read stamp
                if ((s & ABITS) == 0L)
                    break;
                return stamp;
            }
        }
        return 0L;
    }

    public long tryConvertToOptimisticRead(long stamp) {
        long a, m, s, next;
        WNode h;
        VarHandle.acquireFence();
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((a = stamp & ABITS) >= WBIT) {
                // write stamp
                if (s != stamp)
                    break;
                return unlockWriteInternal(s);
            } else if (a == 0L)
                // already an optimistic read stamp
                return stamp;
            else if ((m = s & ABITS) == 0L) // invalid read stamp
                break;
            else if (m < RFULL) {
                if (casState(s, next = s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    return next & SBITS;
                }
            } else if ((next = tryDecReaderOverflow(s)) != 0L)
                return next & SBITS;
        }
        return 0L;
    }

    @ReservedStackAccess
    public boolean tryUnlockWrite() {
        long s;
        if (((s = state) & WBIT) != 0L) {
            unlockWriteInternal(s);
            return true;
        }
        return false;
    }

    @ReservedStackAccess
    public boolean tryUnlockRead() {
        long s, m;
        WNode h;
        while ((m = (s = state) & ABITS) != 0L && m < WBIT) {
            if (m < RFULL) {
                if (casState(s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    return true;
                }
            } else if (tryDecReaderOverflow(s) != 0L)
                return true;
        }
        return false;
    }

    // status monitoring methods

    private int getReadLockCount(long s) {
        long readers;
        if ((readers = s & RBITS) >= RFULL)
            readers = RFULL + readerOverflow;
        return (int)readers;
    }

    public boolean isWriteLocked() {
        return (state & WBIT) != 0L;
    }

    public boolean isReadLocked() {
        return (state & RBITS) != 0L;
    }

    public static boolean isWriteLockStamp(long stamp) {
        return (stamp & ABITS) == WBIT;
    }

    public static boolean isReadLockStamp(long stamp) {
        return (stamp & RBITS) != 0L;
    }

    public static boolean isLockStamp(long stamp) {
        return (stamp & ABITS) != 0L;
    }

    public static boolean isOptimisticReadStamp(long stamp) {
        return (stamp & ABITS) == 0L && stamp != 0L;
    }

    public int getReadLockCount() {
        return getReadLockCount(state);
    }

    public String toString() {
        long s = state;
        return super.toString() + ((s & ABITS) == 0L ? "[Unlocked]"
            : (s & WBIT) != 0L ? "[Write-locked]" : "[Read-locks:" + getReadLockCount(s) + "]");
    }

    // views

    public Lock asReadLock() {
        ReadLockView v;
        if ((v = readLockView) != null)
            return v;
        return readLockView = new ReadLockView();
    }

    public Lock asWriteLock() {
        WriteLockView v;
        if ((v = writeLockView) != null)
            return v;
        return writeLockView = new WriteLockView();
    }

    public ReadWriteLock asReadWriteLock() {
        ReadWriteLockView v;
        if ((v = readWriteLockView) != null)
            return v;
        return readWriteLockView = new ReadWriteLockView();
    }

    // view classes

    final class ReadLockView implements Lock {
        public void lock() {
            readLock();
        }

        public void lockInterruptibly() throws InterruptedException {
            readLockInterruptibly();
        }

        public boolean tryLock() {
            return tryReadLock() != 0L;
        }

        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            return tryReadLock(time, unit) != 0L;
        }

        public void unlock() {
            unstampedUnlockRead();
        }

        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    final class WriteLockView implements Lock {
        public void lock() {
            writeLock();
        }

        public void lockInterruptibly() throws InterruptedException {
            writeLockInterruptibly();
        }

        public boolean tryLock() {
            return tryWriteLock() != 0L;
        }

        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            return tryWriteLock(time, unit) != 0L;
        }

        public void unlock() {
            unstampedUnlockWrite();
        }

        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    final class ReadWriteLockView implements ReadWriteLock {
        public Lock readLock() {
            return asReadLock();
        }

        public Lock writeLock() {
            return asWriteLock();
        }
    }

    // Unlock methods without stamp argument checks for view classes.
    // Needed because view-class lock methods throw away stamps.

    final void unstampedUnlockWrite() {
        long s;
        if (((s = state) & WBIT) == 0L)
            throw new IllegalMonitorStateException();
        unlockWriteInternal(s);
    }

    final void unstampedUnlockRead() {
        long s, m;
        WNode h;
        while ((m = (s = state) & RBITS) > 0L) {
            if (m < RFULL) {
                if (casState(s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    return;
                }
            } else if (tryDecReaderOverflow(s) != 0L)
                return;
        }
        throw new IllegalMonitorStateException();
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        STATE.setVolatile(this, ORIGIN); // reset to unlocked state
    }

    // internals

    private long tryIncReaderOverflow(long s) {
        // assert (s & ABITS) >= RFULL;
        if ((s & ABITS) == RFULL) {
            if (casState(s, s | RBITS)) {
                ++readerOverflow;
                STATE.setVolatile(this, s);
                return s;
            }
        } else if ((LockSupport.nextSecondarySeed() & OVERFLOW_YIELD_RATE) == 0)
            Thread.yield();
        else
            Thread.onSpinWait();
        return 0L;
    }

    private long tryDecReaderOverflow(long s) {
        // assert (s & ABITS) >= RFULL;
        if ((s & ABITS) == RFULL) {
            if (casState(s, s | RBITS)) {
                int r;
                long next;
                if ((r = readerOverflow) > 0) {
                    readerOverflow = r - 1;
                    next = s;
                } else
                    next = s - RUNIT;
                STATE.setVolatile(this, next);
                return next;
            }
        } else if ((LockSupport.nextSecondarySeed() & OVERFLOW_YIELD_RATE) == 0)
            Thread.yield();
        else
            Thread.onSpinWait();
        return 0L;
    }

    private void release(WNode h) {
        if (h != null) {
            WNode q;
            Thread w;
            WSTATUS.compareAndSet(h, WAITING, 0);
            if ((q = h.next) == null || q.status == CANCELLED) {
                for (WNode t = wtail; t != null && t != h; t = t.prev)
                    if (t.status <= 0)
                        q = t;
            }
            if (q != null && (w = q.thread) != null)
                LockSupport.unpark(w);
        }
    }

    /**
     *
     * 整个acquireWrite(...)方法是两个大的for循环，内部实现了非常复杂的自旋策略。在第一个大的for循环里面，目的就是把该Node加入队列的尾部，
     * 一边加入，一边通过CAS操作尝试获得锁。如果获得了，整个方法就会返回；如果不能获得锁，会一直自旋，直到加入队列尾部。
     *
     * 在第二个大的for循环里，也就是该Node已经在队列尾部了。这个时候，如果发现自己刚好也在队列头部，说明队列中除了空的Head节点，就是当前线程了。
     * 此时，再进行新一轮的自旋，直到达到MAX_HEAD_SPINS次数，然后进入阻塞。这里有一个关键点要说明：当release(...)方法被调用之后，会唤醒队列头部的第1个元素，
     * 此时会执行第二个大的for循环里面的逻辑，也就是接着for循环里面park()方法后面的代码往下执行。
     *
     * 另外一个不同于AQS的阻塞队列的地方是，在每个WNode里面有一个cowait指针，用于串联起所有的读线程。
     * 例如，队列尾部阻塞的是一个读线程 1，现在又来了读线程 2、3，那么会通过cowait指针，把1、2、3串联起来。1被唤醒之后，2、3也随之一起被唤醒，因为读和读之间不互斥。
     * @date 2022/6/19 23:08
     * @param interruptible
     * @param deadline
     * @return long
     */
    private long acquireWrite(boolean interruptible, long deadline) {
        WNode node = null, p;
        // 入列时自旋
        for (int spins = -1;;) { // spin while enqueuing
            long m, s, ns;
            if ((m = (s = state) & ABITS) == 0L) {
                if ((ns = tryWriteLock(s)) != 0L)
                    // 自旋的时候获取到锁，返回
                    return ns;
            } else if (spins < 0)
                // 计算自旋值
                spins = (m == WBIT && wtail == whead) ? SPINS : 0;
            else if (spins > 0) {
                // 每次自旋获取锁，spins值减一
                --spins;
                Thread.onSpinWait();
            } else if ((p = wtail) == null) { // 如果尾部节点是null，初始化队列
                WNode hd = new WNode(WMODE, null);
                // 头部和尾部指向一个节点
                if (WHEAD.weakCompareAndSet(this, null, hd))
                    wtail = hd;
            } else if (node == null)
                node = new WNode(WMODE, p);
            else if (node.prev != p)
                // p节点作为前置节点
                node.prev = p;
            else if (WTAIL.weakCompareAndSet(this, p, node)) { // for循环唯一的break，成功将节点node添加到队列尾部，才会退出for循环
                // 设置p的后置节点为node
                p.next = node;
                break;
            }
        }

        boolean wasInterrupted = false;
        for (int spins = -1;;) {
            WNode h, np, pp;
            int ps;
            if ((h = whead) == p) {
                if (spins < 0)
                    spins = HEAD_SPINS;
                else if (spins < MAX_HEAD_SPINS)
                    spins <<= 1;
                for (int k = spins; k > 0; --k) { // spin at head
                    long s, ns;
                    if (((s = state) & ABITS) == 0L) {
                        if ((ns = tryWriteLock(s)) != 0L) {
                            whead = node;
                            node.prev = null;
                            if (wasInterrupted)
                                Thread.currentThread().interrupt();
                            return ns;
                        }
                    } else
                        Thread.onSpinWait();
                }
            } else if (h != null) { // help release stale waiters
                WNode c;
                Thread w;
                while ((c = h.cowait) != null) {
                    if (WCOWAIT.weakCompareAndSet(h, c, c.cowait) && (w = c.thread) != null)
                        LockSupport.unpark(w);
                }
            }
            if (whead == h) {
                if ((np = node.prev) != p) {
                    if (np != null)
                        (p = np).next = node; // stale
                } else if ((ps = p.status) == 0)
                    WSTATUS.compareAndSet(p, 0, WAITING);
                else if (ps == CANCELLED) {
                    if ((pp = p.prev) != null) {
                        node.prev = pp;
                        pp.next = node;
                    }
                } else {
                    long time; // 0 argument to park means no timeout
                    if (deadline == 0L)
                        time = 0L;
                    else if ((time = deadline - System.nanoTime()) <= 0L)
                        return cancelWaiter(node, node, false);
                    Thread wt = Thread.currentThread();
                    node.thread = wt;
                    if (p.status < 0 && (p != h || (state & ABITS) != 0L) && whead == h && node.prev == p) {
                        if (time == 0L)
                            // 阻塞，直到被唤醒
                            LockSupport.park(this);
                        else // 计时阻塞
                            LockSupport.parkNanos(this, time);
                    }
                    node.thread = null;
                    if (Thread.interrupted()) {
                        if (interruptible)
                            // 如果被中断了，则取消等待
                            return cancelWaiter(node, node, true);
                        wasInterrupted = true;
                    }
                }
            }
        }
    }

    private long acquireRead(boolean interruptible, long deadline) {
        boolean wasInterrupted = false;
        WNode node = null, p;
        for (int spins = -1;;) {
            WNode h;
            if ((h = whead) == (p = wtail)) {
                for (long m, s, ns;;) {
                    if ((m = (s = state) & ABITS) < RFULL ? casState(s, ns = s + RUNIT)
                        : (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L)) {
                        if (wasInterrupted)
                            Thread.currentThread().interrupt();
                        return ns;
                    } else if (m >= WBIT) {
                        if (spins > 0) {
                            --spins;
                            Thread.onSpinWait();
                        } else {
                            if (spins == 0) {
                                WNode nh = whead, np = wtail;
                                if ((nh == h && np == p) || (h = nh) != (p = np))
                                    break;
                            }
                            spins = SPINS;
                        }
                    }
                }
            }
            if (p == null) { // initialize queue
                WNode hd = new WNode(WMODE, null);
                if (WHEAD.weakCompareAndSet(this, null, hd))
                    wtail = hd;
            } else if (node == null)
                node = new WNode(RMODE, p);
            else if (h == p || p.mode != RMODE) {
                if (node.prev != p)
                    node.prev = p;
                else if (WTAIL.weakCompareAndSet(this, p, node)) {
                    p.next = node;
                    break;
                }
            } else if (!WCOWAIT.compareAndSet(p, node.cowait = p.cowait, node))
                node.cowait = null;
            else {
                for (;;) {
                    WNode pp, c;
                    Thread w;
                    if ((h = whead) != null && (c = h.cowait) != null && WCOWAIT.compareAndSet(h, c, c.cowait)
                        && (w = c.thread) != null) // help release
                        LockSupport.unpark(w);
                    if (Thread.interrupted()) {
                        if (interruptible)
                            return cancelWaiter(node, p, true);
                        wasInterrupted = true;
                    }
                    if (h == (pp = p.prev) || h == p || pp == null) {
                        long m, s, ns;
                        do {
                            if ((m = (s = state) & ABITS) < RFULL ? casState(s, ns = s + RUNIT)
                                : (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L)) {
                                if (wasInterrupted)
                                    Thread.currentThread().interrupt();
                                return ns;
                            }
                        } while (m < WBIT);
                    }
                    if (whead == h && p.prev == pp) {
                        long time;
                        if (pp == null || h == p || p.status > 0) {
                            node = null; // throw away
                            break;
                        }
                        if (deadline == 0L)
                            time = 0L;
                        else if ((time = deadline - System.nanoTime()) <= 0L) {
                            if (wasInterrupted)
                                Thread.currentThread().interrupt();
                            return cancelWaiter(node, p, false);
                        }
                        Thread wt = Thread.currentThread();
                        node.thread = wt;
                        if ((h != pp || (state & ABITS) == WBIT) && whead == h && p.prev == pp) {
                            if (time == 0L)
                                LockSupport.park(this);
                            else
                                LockSupport.parkNanos(this, time);
                        }
                        node.thread = null;
                    }
                }
            }
        }

        for (int spins = -1;;) {
            WNode h, np, pp;
            int ps;
            if ((h = whead) == p) {
                if (spins < 0)
                    spins = HEAD_SPINS;
                else if (spins < MAX_HEAD_SPINS)
                    spins <<= 1;
                for (int k = spins;;) { // spin at head
                    long m, s, ns;
                    if ((m = (s = state) & ABITS) < RFULL ? casState(s, ns = s + RUNIT)
                        : (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L)) {
                        WNode c;
                        Thread w;
                        whead = node;
                        node.prev = null;
                        while ((c = node.cowait) != null) {
                            if (WCOWAIT.compareAndSet(node, c, c.cowait) && (w = c.thread) != null)
                                LockSupport.unpark(w);
                        }
                        if (wasInterrupted)
                            Thread.currentThread().interrupt();
                        return ns;
                    } else if (m >= WBIT && --k <= 0)
                        break;
                    else
                        Thread.onSpinWait();
                }
            } else if (h != null) {
                WNode c;
                Thread w;
                while ((c = h.cowait) != null) {
                    if (WCOWAIT.compareAndSet(h, c, c.cowait) && (w = c.thread) != null)
                        LockSupport.unpark(w);
                }
            }
            if (whead == h) {
                if ((np = node.prev) != p) {
                    if (np != null)
                        (p = np).next = node; // stale
                } else if ((ps = p.status) == 0)
                    WSTATUS.compareAndSet(p, 0, WAITING);
                else if (ps == CANCELLED) {
                    if ((pp = p.prev) != null) {
                        node.prev = pp;
                        pp.next = node;
                    }
                } else {
                    long time;
                    if (deadline == 0L)
                        time = 0L;
                    else if ((time = deadline - System.nanoTime()) <= 0L)
                        return cancelWaiter(node, node, false);
                    Thread wt = Thread.currentThread();
                    node.thread = wt;
                    if (p.status < 0 && (p != h || (state & ABITS) == WBIT) && whead == h && node.prev == p) {
                        if (time == 0L)
                            LockSupport.park(this);
                        else
                            LockSupport.parkNanos(this, time);
                    }
                    node.thread = null;
                    if (Thread.interrupted()) {
                        if (interruptible)
                            return cancelWaiter(node, node, true);
                        wasInterrupted = true;
                    }
                }
            }
        }
    }

    private long cancelWaiter(WNode node, WNode group, boolean interrupted) {
        if (node != null && group != null) {
            Thread w;
            node.status = CANCELLED;
            // unsplice cancelled nodes from group
            for (WNode p = group, q; (q = p.cowait) != null;) {
                if (q.status == CANCELLED) {
                    WCOWAIT.compareAndSet(p, q, q.cowait);
                    p = group; // restart
                } else
                    p = q;
            }
            if (group == node) {
                for (WNode r = group.cowait; r != null; r = r.cowait) {
                    if ((w = r.thread) != null)
                        LockSupport.unpark(w); // wake up uncancelled co-waiters
                }
                for (WNode pred = node.prev; pred != null;) { // unsplice
                    WNode succ, pp; // find valid successor
                    while ((succ = node.next) == null || succ.status == CANCELLED) {
                        WNode q = null; // find successor the slow way
                        for (WNode t = wtail; t != null && t != node; t = t.prev)
                            if (t.status != CANCELLED)
                                q = t; // don't link if succ cancelled
                        if (succ == q || // ensure accurate successor
                            WNEXT.compareAndSet(node, succ, succ = q)) {
                            if (succ == null && node == wtail)
                                WTAIL.compareAndSet(this, node, pred);
                            break;
                        }
                    }
                    if (pred.next == node) // unsplice pred link
                        WNEXT.compareAndSet(pred, node, succ);
                    if (succ != null && (w = succ.thread) != null) {
                        // wake up succ to observe new pred
                        succ.thread = null;
                        LockSupport.unpark(w);
                    }
                    if (pred.status != CANCELLED || (pp = pred.prev) == null)
                        break;
                    node.prev = pp; // repeat if new pred wrong/cancelled
                    WNEXT.compareAndSet(pp, pred, succ);
                    pred = pp;
                }
            }
        }
        WNode h; // Possibly release first waiter
        while ((h = whead) != null) {
            long s;
            WNode q; // similar to release() but check eligibility
            if ((q = h.next) == null || q.status == CANCELLED) {
                for (WNode t = wtail; t != null && t != h; t = t.prev)
                    if (t.status <= 0)
                        q = t;
            }
            if (h == whead) {
                if (q != null && h.status == 0 && ((s = state) & ABITS) != WBIT && // waiter is eligible
                    (s == 0L || q.mode == RMODE))
                    release(h);
                break;
            }
        }
        return (interrupted || Thread.interrupted()) ? INTERRUPTED : 0L;
    }

    // VarHandle mechanics
    private static final VarHandle STATE;
    private static final VarHandle WHEAD;
    private static final VarHandle WTAIL;
    private static final VarHandle WNEXT;
    private static final VarHandle WSTATUS;
    private static final VarHandle WCOWAIT;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            STATE = l.findVarHandle(StampedLock.class, "state", long.class);
            WHEAD = l.findVarHandle(StampedLock.class, "whead", WNode.class);
            WTAIL = l.findVarHandle(StampedLock.class, "wtail", WNode.class);
            WSTATUS = l.findVarHandle(WNode.class, "status", int.class);
            WNEXT = l.findVarHandle(WNode.class, "next", WNode.class);
            WCOWAIT = l.findVarHandle(WNode.class, "cowait", WNode.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
