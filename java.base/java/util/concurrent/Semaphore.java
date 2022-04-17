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

import java.util.Collection;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * 信号量：提供了资源数量的并发访问控制
 *
 * @author liuzhen
 * @date 2022/4/16 17:44
 * @return
 */
public class Semaphore implements java.io.Serializable {
    private static final long serialVersionUID = -3222578661600680210L;
    private final Sync sync;

    /**
     *
     * @author liuzhen
     * @date 2022/4/16 17:47
     * @return
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 1192457210091910933L;

        Sync(int permits) {
            setState(permits);
        }

        final int getPermits() {
            return getState();
        }

        final int nonfairTryAcquireShared(int acquires) {
            for (; ; ) {
                int available = getState();
                int remaining = available - acquires;
                if (remaining < 0 || compareAndSetState(available, remaining))
                    return remaining;
            }
        }

        protected final boolean tryReleaseShared(int releases) {
            for (; ; ) {
                int current = getState();
                int next = current + releases;
                if (next < current) // overflow
                    throw new Error("Maximum permit count exceeded");
                if (compareAndSetState(current, next))
                    return true;
            }
        }

        final void reducePermits(int reductions) {
            for (; ; ) {
                int current = getState();
                int next = current - reductions;
                if (next > current) // underflow
                    throw new Error("Permit count underflow");
                if (compareAndSetState(current, next))
                    return;
            }
        }

        final int drainPermits() {
            for (; ; ) {
                int current = getState();
                if (current == 0 || compareAndSetState(current, 0))
                    return current;
            }
        }
    }

    /**
     * 非公平锁
     * @author liuzhen
     * @date 2022/4/16 17:47
     * @param null
     * @return
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = -2694183684443567898L;

        NonfairSync(int permits) {
            super(permits);
        }

        protected int tryAcquireShared(int acquires) {
            return nonfairTryAcquireShared(acquires);
        }
    }

    /**
     * 公平锁
     * @author liuzhen
     * @date 2022/4/16 17:46
     * @return
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = 2014338818796000944L;

        FairSync(int permits) {
            super(permits);
        }

        protected int tryAcquireShared(int acquires) {
            for (; ; ) {
                if (hasQueuedPredecessors())
                    return -1;
                int available = getState();
                int remaining = available - acquires;
                if (remaining < 0 || compareAndSetState(available, remaining))
                    return remaining;
            }
        }
    }

    public Semaphore(int permits) {
        sync = new NonfairSync(permits);
    }

    public Semaphore(int permits, boolean fair) {
        sync = fair ? new FairSync(permits) : new NonfairSync(permits);
    }

    public void acquire() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

    public void acquireUninterruptibly() {
        sync.acquireShared(1);
    }

    public boolean tryAcquire() {
        return sync.nonfairTryAcquireShared(1) >= 0;
    }

    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    /**
     * 释放资源
     * 由于Semaphore和锁的实现原理基本相同，上面的代码不再展开解释。资源总数即state的初始
     * 值，在acquire里对state变量进行CAS减操作，减到0之后，线程阻塞；在release里对state变量进行CAS加操作。
     *
     * @param
     * @return void
     * @author liuzhen
     * @date 2022/4/16 17:45
     */
    public void release() {
        sync.releaseShared(1);
    }

    public void release(int permits) {
        if (permits < 0)
            throw new IllegalArgumentException();
        sync.releaseShared(permits);
    }

    public void acquire(int permits) throws InterruptedException {
        if (permits < 0)
            throw new IllegalArgumentException();
        sync.acquireSharedInterruptibly(permits);
    }

    public void acquireUninterruptibly(int permits) {
        if (permits < 0)
            throw new IllegalArgumentException();
        sync.acquireShared(permits);
    }

    public boolean tryAcquire(int permits) {
        if (permits < 0)
            throw new IllegalArgumentException();
        return sync.nonfairTryAcquireShared(permits) >= 0;
    }

    public boolean tryAcquire(int permits, long timeout, TimeUnit unit) throws InterruptedException {
        if (permits < 0)
            throw new IllegalArgumentException();
        return sync.tryAcquireSharedNanos(permits, unit.toNanos(timeout));
    }

    public int availablePermits() {
        return sync.getPermits();
    }

    public int drainPermits() {
        return sync.drainPermits();
    }

    protected void reducePermits(int reduction) {
        if (reduction < 0)
            throw new IllegalArgumentException();
        sync.reducePermits(reduction);
    }

    public boolean isFair() {
        return sync instanceof FairSync;
    }

    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    public String toString() {
        return super.toString() + "[Permits = " + sync.getPermits() + "]";
    }
}
