/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent.locks;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import jdk.internal.vm.annotation.ReservedStackAccess;

/**
 * 读写锁实现
 *
 * @date 2022/6/19 19:11
 */
public class ReentrantReadWriteLock implements ReadWriteLock, java.io.Serializable {
    private static final long serialVersionUID = -6992448646407690164L;
    private final ReentrantReadWriteLock.ReadLock readerLock;
    private final ReentrantReadWriteLock.WriteLock writerLock;
    final Sync sync;

    public ReentrantReadWriteLock() {
        this(false);
    }

    public ReentrantReadWriteLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
        readerLock = new ReadLock(this);
        writerLock = new WriteLock(this);
    }

    /**
     * 获取写锁
     * @date 2022/6/19 19:12
     * @param
     * @return java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock
     */
    public ReentrantReadWriteLock.WriteLock writeLock() {
        return writerLock;
    }

    /**
     * 获取读锁
     * @date 2022/6/19 19:12
     * @param
     * @return java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock
     */
    public ReentrantReadWriteLock.ReadLock readLock() {
        return readerLock;
    }

    /**
     *
     * @date 2022/6/19 19:11
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 6317671515068378041L;

        static final int SHARED_SHIFT = 16;
        static final int SHARED_UNIT = (1 << SHARED_SHIFT);
        static final int MAX_COUNT = (1 << SHARED_SHIFT) - 1;
        static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

        /**
         * 持有读锁的线程的重入次数
         * @date 2022/6/19 18:42
         * @param c
         * @return int
         */
        static int sharedCount(int c) {
            return c >>> SHARED_SHIFT;
        }

        /**
         * 持有写锁的线程的重入次数
         * @date 2022/6/19 18:42
         * @param c
         * @return int
         */
        static int exclusiveCount(int c) {
            return c & EXCLUSIVE_MASK;
        }

        static final class HoldCounter {
            int count;          // initially 0
            // Use id, not reference, to avoid garbage retention
            final long tid = LockSupport.getThreadId(Thread.currentThread());
        }

        static final class ThreadLocalHoldCounter extends ThreadLocal<HoldCounter> {
            public HoldCounter initialValue() {
                return new HoldCounter();
            }
        }

        private transient ThreadLocalHoldCounter readHolds;

        private transient HoldCounter cachedHoldCounter;

        private transient Thread firstReader;
        private transient int firstReaderHoldCount;

        Sync() {
            readHolds = new ThreadLocalHoldCounter();
            setState(getState()); // ensures visibility of readHolds
        }


        abstract boolean readerShouldBlock();

        abstract boolean writerShouldBlock();

        @ReservedStackAccess
        protected final boolean tryRelease(int releases) {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            int nextc = getState() - releases;
            boolean free = exclusiveCount(nextc) == 0;
            if (free)
                setExclusiveOwnerThread(null);
            setState(nextc);
            return free;
        }

        @ReservedStackAccess
        protected final boolean tryAcquire(int acquires) {
            Thread current = Thread.currentThread();
            int c = getState();
            int w = exclusiveCount(c);
            if (c != 0) {
                // (Note: if c != 0 and w == 0 then shared count != 0)
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;
                if (w + exclusiveCount(acquires) > MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                // Reentrant acquire
                setState(c + acquires);
                return true;
            }
            if (writerShouldBlock() || !compareAndSetState(c, c + acquires))
                return false;
            setExclusiveOwnerThread(current);
            return true;
        }

        /**
         * 因为读锁是共享锁，多个线程会同时持有读锁，所以对读锁的释放不能直接减1，而是需要通过一个for循环+CAS操作不断重试。
         * 这是tryReleaseShared和tryRelease的根本差异所在。
         * @date 2022/6/19 19:39
         * @param unused
         * @return boolean
         */
        @ReservedStackAccess
        protected final boolean tryReleaseShared(int unused) {
            Thread current = Thread.currentThread();
            if (firstReader == current) {
                // assert firstReaderHoldCount > 0;
                if (firstReaderHoldCount == 1)
                    firstReader = null;
                else
                    firstReaderHoldCount--;
            } else {
                HoldCounter rh = cachedHoldCounter;
                if (rh == null || rh.tid != LockSupport.getThreadId(current))
                    rh = readHolds.get();
                int count = rh.count;
                if (count <= 1) {
                    readHolds.remove();
                    if (count <= 0)
                        throw unmatchedUnlockException();
                }
                --rh.count;
            }
            for (; ; ) {
                int c = getState();
                int nextc = c - SHARED_UNIT;
                if (compareAndSetState(c, nextc))
                    // Releasing the read lock has no effect on readers,
                    // but it may allow waiting writers to proceed if
                    // both read and write locks are now free.
                    return nextc == 0;
            }
        }

        private static IllegalMonitorStateException unmatchedUnlockException() {
            return new IllegalMonitorStateException("attempt to unlock read lock, not locked by current thread");
        }

        @ReservedStackAccess
        protected final int tryAcquireShared(int unused) {
            Thread current = Thread.currentThread();
            int c = getState();
            if (exclusiveCount(c) != 0 && getExclusiveOwnerThread() != current)
                return -1;
            int r = sharedCount(c);
            if (!readerShouldBlock() && r < MAX_COUNT && compareAndSetState(c, c + SHARED_UNIT)) {
                if (r == 0) {
                    firstReader = current;
                    firstReaderHoldCount = 1;
                } else if (firstReader == current) {
                    firstReaderHoldCount++;
                } else {
                    HoldCounter rh = cachedHoldCounter;
                    if (rh == null || rh.tid != LockSupport.getThreadId(current))
                        cachedHoldCounter = rh = readHolds.get();
                    else if (rh.count == 0)
                        readHolds.set(rh);
                    rh.count++;
                }
                return 1;
            }
            return fullTryAcquireShared(current);
        }

        final int fullTryAcquireShared(Thread current) {
            /*
             * This code is in part redundant with that in
             * tryAcquireShared but is simpler overall by not
             * complicating tryAcquireShared with interactions between
             * retries and lazily reading hold counts.
             */
            HoldCounter rh = null;
            for (; ; ) {
                int c = getState();
                if (exclusiveCount(c) != 0) {
                    if (getExclusiveOwnerThread() != current)
                        return -1;
                    // else we hold the exclusive lock; blocking here
                    // would cause deadlock.
                } else if (readerShouldBlock()) {
                    // Make sure we're not acquiring read lock reentrantly
                    if (firstReader == current) {
                        // assert firstReaderHoldCount > 0;
                    } else {
                        if (rh == null) {
                            rh = cachedHoldCounter;
                            if (rh == null || rh.tid != LockSupport.getThreadId(current)) {
                                rh = readHolds.get();
                                if (rh.count == 0)
                                    readHolds.remove();
                            }
                        }
                        if (rh.count == 0)
                            return -1;
                    }
                }
                if (sharedCount(c) == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                    if (sharedCount(c) == 0) {
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) {
                        firstReaderHoldCount++;
                    } else {
                        if (rh == null)
                            rh = cachedHoldCounter;
                        if (rh == null || rh.tid != LockSupport.getThreadId(current))
                            rh = readHolds.get();
                        else if (rh.count == 0)
                            readHolds.set(rh);
                        rh.count++;
                        cachedHoldCounter = rh; // cache for release
                    }
                    return 1;
                }
            }
        }

        /**
         * 尝试获取写锁
         * @date 2022/6/19 19:14
         * @param
         * @return boolean
         */
        @ReservedStackAccess
        final boolean tryWriteLock() {
            Thread current = Thread.currentThread();
            int c = getState();
            // 当state不是0的时候，如果写线程获取锁的个数是0，或者写线程不是当前线程，则返回抢锁失败
            if (c != 0) {
                int w = exclusiveCount(c);
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;
                if (w == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
            }

            // 只要不是上面的情况，则通过CAS设置state的值，如果设置成功，就将排他线程设置为当前线程，并返回true。
            if (!compareAndSetState(c, c + 1))
                return false;
            setExclusiveOwnerThread(current);
            return true;
        }

        /**
         * 尝试获取读锁
         * @date 2022/6/19 19:37
         * @param
         * @return boolean
         */
        @ReservedStackAccess
        final boolean tryReadLock() {
            // 获取当前线程
            Thread current = Thread.currentThread();
            for (; ; ) {
                // 获取state值
                int c = getState();
                // 如果是写线程占用锁或者当前线程不是排他线程，则抢锁失败
                if (exclusiveCount(c) != 0 && getExclusiveOwnerThread() != current)
                    return false;
                // 获取读锁state值
                int r = sharedCount(c);
                // 如果获取锁的值达到极限，则抛异常
                if (r == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");

                // 使用CAS设置读线程锁state值
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                    // 如果r=0，则当前线程就是第一个读线程
                    if (r == 0) {
                        firstReader = current;
                        // 读线程个数为1
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) { // 如果写线程是当前线程
                        // 如果第一个读线程就是当前线程，表示读线程重入读锁
                        firstReaderHoldCount++;
                    } else { // 如果firstReader不是当前线程，则从ThreadLocal中获取当前线程的读锁个数，并设置当前线程持有的读锁个数
                        HoldCounter rh = cachedHoldCounter;
                        if (rh == null || rh.tid != LockSupport.getThreadId(current))
                            cachedHoldCounter = rh = readHolds.get();
                        else if (rh.count == 0)
                            readHolds.set(rh);
                        rh.count++;
                    }
                    return true;
                }
            }
        }

        protected final boolean isHeldExclusively() {
            // While we must in general read state before owner,
            // we don't need to do so to check if current thread is owner
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        // Methods relayed to outer class

        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        final Thread getOwner() {
            // Must read state before owner to ensure memory consistency
            return ((exclusiveCount(getState()) == 0) ? null : getExclusiveOwnerThread());
        }

        final int getReadLockCount() {
            return sharedCount(getState());
        }

        final boolean isWriteLocked() {
            return exclusiveCount(getState()) != 0;
        }

        final int getWriteHoldCount() {
            return isHeldExclusively() ? exclusiveCount(getState()) : 0;
        }

        final int getReadHoldCount() {
            if (getReadLockCount() == 0)
                return 0;

            Thread current = Thread.currentThread();
            if (firstReader == current)
                return firstReaderHoldCount;

            HoldCounter rh = cachedHoldCounter;
            if (rh != null && rh.tid == LockSupport.getThreadId(current))
                return rh.count;

            int count = readHolds.get().count;
            if (count == 0)
                readHolds.remove();
            return count;
        }

        /**
         * Reconstitutes the instance from a stream (that is, deserializes it).
         */
        private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            readHolds = new ThreadLocalHoldCounter();
            setState(0); // reset to unlocked state
        }

        final int getCount() {
            return getState();
        }
    }

    /**
     * 非公平锁实现
     * 对于非公平，读锁和写锁的实现策略略有差异：
     * 1. 写线程能抢锁，前提是state=0，只有在没有其他线程持有读锁或写锁的情况下，它才有机会去抢锁。
     *  或者state != 0，但那个持有写锁的线程是它自己，再次重入。写线程是非公平的，即writerShouldBlock()方法一直返回false。
     * 2. 对于读线程，假设当前线程被读线程持有，然后其他读线程还非公平地一直去抢，可能导致写线程永远拿不到锁，所以对于读线程的非公平，要做一些“约束”。
     *  当发现队列的第1个元素是写线程的时候，读线程也要阻塞，不能直接去抢。即偏向写线程。
     * @date 2022/6/19 19:05
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = -8159625535654395037L;

        /**
         * 写线程抢锁的时候是否应该阻塞
         * @date 2022/6/19 19:05
         * @param
         * @return boolean
         */
        final boolean writerShouldBlock() {
            // 写线程在抢锁之前永远不被阻塞，非公平锁
            return false;
        }

        /**
         * 读线程抢锁的时候是否应该阻塞
         * @date 2022/6/19 19:06
         * @param
         * @return boolean
         */
        final boolean readerShouldBlock() {
            // 读线程抢锁的时候，当队列中第一个元素是写线程的时候要阻塞
            return apparentlyFirstQueuedIsExclusive();
        }
    }

    /**
     * 公平锁实现
     * 对于公平，比较容易理解，不论是读锁，还是写锁，只要队列中有其他线程在排队（排队等读锁，或者排队等写锁），就不能直接去抢锁，要排在队列尾部。
     * @date 2022/6/19 19:05
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -2274990926593161451L;

        /** 
         * 写线程抢锁的时候是否应该阻塞
         * @date 2022/6/19 19:06 
         * @param  
         * @return boolean
         */
        final boolean writerShouldBlock() {
            // 写线程在抢锁之前，如果队列中有其他线程在排队，则阻塞。公平锁
            return hasQueuedPredecessors();
        }

        /**
         * 读线程抢锁的时候是否应该阻塞
         * @date 2022/6/19 19:06 
         * @param  
         * @return boolean
         */
        final boolean readerShouldBlock() {
            // 读线程在抢锁之前，如果队列中有其他线程在排队，阻塞。公平锁
            return hasQueuedPredecessors();
        }
    }

    /**
     * 读锁
     * @date 2022/6/19 19:11
     */
    public static class ReadLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -5992448646407690164L;
        private final Sync sync;

        protected ReadLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }

        /**
         *
         * @date 2022/6/19 19:35
         * @param
         * @return void
         */
        public void lock() {
            sync.acquireShared(1);
        }

        public void lockInterruptibly() throws InterruptedException {
            sync.acquireSharedInterruptibly(1);
        }

        /**
         *
         * @date 2022/6/19 19:35
         * @param
         * @return boolean
         */
        public boolean tryLock() {
            return sync.tryReadLock();
        }

        public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
        }

        public void unlock() {
            sync.releaseShared(1);
        }

        /**
         * 读锁不支持Condition
         * @date 2022/6/19 19:47
         * @param
         * @return java.util.concurrent.locks.Condition
         */
        public Condition newCondition() {
            // 抛异常
            throw new UnsupportedOperationException();
        }

        public String toString() {
            int r = sync.getReadLockCount();
            return super.toString() + "[Read locks = " + r + "]";
        }
    }

    /**
     * 写锁
     * @date 2022/6/19 19:10
     */
    public static class WriteLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -4992448646407690164L;
        private final Sync sync;

        protected WriteLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }

        public void lock() {
            sync.acquire(1);
        }

        public void lockInterruptibly() throws InterruptedException {
            sync.acquireInterruptibly(1);
        }

        /**
         * 尝试获取锁
         * @date 2022/6/19 19:14
         * @param
         * @return boolean
         */
        public boolean tryLock() {
            return sync.tryWriteLock();
        }

        public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            return sync.tryAcquireNanos(1, unit.toNanos(timeout));
        }

        public void unlock() {
            sync.release(1);
        }

        /**
         * 获取Condition
         * @date 2022/6/19 19:48
         * @param
         * @return java.util.concurrent.locks.Condition
         */
        public Condition newCondition() {
            return sync.newCondition();
        }

        public String toString() {
            Thread o = sync.getOwner();
            return super.toString() + ((o == null) ? "[Unlocked]" : "[Locked by thread " + o.getName() + "]");
        }

        public boolean isHeldByCurrentThread() {
            return sync.isHeldExclusively();
        }

        public int getHoldCount() {
            return sync.getWriteHoldCount();
        }
    }

    // Instrumentation and status

    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    protected Thread getOwner() {
        return sync.getOwner();
    }

    public int getReadLockCount() {
        return sync.getReadLockCount();
    }

    public boolean isWriteLocked() {
        return sync.isWriteLocked();
    }

    public boolean isWriteLockedByCurrentThread() {
        return sync.isHeldExclusively();
    }

    public int getWriteHoldCount() {
        return sync.getWriteHoldCount();
    }

    public int getReadHoldCount() {
        return sync.getReadHoldCount();
    }

    protected Collection<Thread> getQueuedWriterThreads() {
        return sync.getExclusiveQueuedThreads();
    }

    protected Collection<Thread> getQueuedReaderThreads() {
        return sync.getSharedQueuedThreads();
    }

    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    public boolean hasWaiters(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    public int getWaitQueueLength(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject)condition);
    }

    public String toString() {
        int c = sync.getCount();
        int w = Sync.exclusiveCount(c);
        int r = Sync.sharedCount(c);

        return super.toString() + "[Write locks = " + w + ", Read locks = " + r + "]";
    }

}
