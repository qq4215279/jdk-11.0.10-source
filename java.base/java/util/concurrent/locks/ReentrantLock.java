/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent.locks;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import jdk.internal.vm.annotation.ReservedStackAccess;

/**
 * ReentrantLock本身没有代码逻辑，实现都在其内部类Sync中
 * @author liuzhen
 * @date 2022/4/17 13:19
 */
public class ReentrantLock implements Lock, java.io.Serializable {
    private static final long serialVersionUID = 7373984872572414699L;

    private final Sync sync;

    /**
     * Sync
     * Sync是一个抽象类，它有两个子类FairSync与NonfairSync，分别对应公平锁和非公平锁。
     * 从下面的ReentrantLock构造方法可以看出，会传入一个布尔类型的变量fair指定锁是公平的还是非公平的，默认为非公平的。
     *
     * 什么叫公平锁和非公平锁呢？
     * 先举个现实生活中的例子，一个人去火车站售票窗口买票，发现现场有人排队，于是他排在队伍末尾，遵循先到者优先服务的规则，这叫公平；
     * 如果他去了不排队，直接冲到窗口买票，这叫作不公平。
     * 对应到锁的例子，一个新的线程来了之后，看到有很多线程在排队，自己排到队伍末尾，这叫公平；
     * 线程来了之后直接去抢锁，这叫作不公平。默认设置的是非公平锁，其实是为了提高效率，减少线程切换。
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -5179523762034025860L;

        /** 
         * 此处没有考虑队列中有没有其他线程，直接使用当前线程获取锁，不排队，不公平
         * @author liuzhen
         * @date 2022/4/17 17:32
         * @param acquires 
         * @return boolean
         */
        @ReservedStackAccess
        final boolean nonfairTryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            // 如果state为0，直接将当前线程设置为排他线程，同时设置state的值
            if (c == 0) {
                if (compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            } else if (current == getExclusiveOwnerThread()) { // 如果state不是0，但是排他线程就是当前线程，则直接设置state的值
                int nextc = c + acquires;
                if (nextc < 0) // overflow
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }

            // 否则返回false，获取失败
            return false;
        }

        /**
         * 尝试释放锁
         * @date 2022/7/14 20:53
         * @param releases
         * @return boolean
         */
        @ReservedStackAccess
        protected final boolean tryRelease(int releases) {
            int c = getState() - releases;
            if (Thread.currentThread() != getExclusiveOwnerThread())
                throw new IllegalMonitorStateException();

            boolean free = false;
            if (c == 0) {
                free = true;
                setExclusiveOwnerThread(null);
            }
            setState(c);
            return free;
        }

        protected final boolean isHeldExclusively() {
            // While we must in general read state before owner,
            // we don't need to do so to check if current thread is owner
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        // Methods relayed from outer class

        final Thread getOwner() {
            return getState() == 0 ? null : getExclusiveOwnerThread();
        }

        final int getHoldCount() {
            return isHeldExclusively() ? getState() : 0;
        }

        final boolean isLocked() {
            return getState() != 0;
        }

        private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            setState(0); // reset to unlocked state
        }
    }

    /**
     * 非公平锁
     * @author liuzhen
     * @date 2022/4/17 13:20
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = 7316153563782823691L;

        /**
         * 尝试获取非公平锁
         * @date 2022/6/19 17:59
         * @param acquires
         * @return boolean
         */
        protected final boolean tryAcquire(int acquires) {
            return nonfairTryAcquire(acquires);
        }
    }

    /**
     * 公平锁
     * @author liuzhen
     * @date 2022/4/17 13:20
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -3000897897090466540L;

        /**
         * 尝试获取公平锁，尝试成功的话，返回true；尝试失败的话，返回false，后续再通过其它办法来获取该锁。
         * @author liuzhen
         * @date 2022/4/17 17:35
         * @param acquires
         * @return boolean
         */
        @ReservedStackAccess
        protected final boolean tryAcquire(int acquires) {
            // 获取“当前线程”
            final Thread current = Thread.currentThread();
            // 获取“独占锁”的状态
            int c = getState();
            // 如果state为0，且队列中没有等待线程，则设置当前线程为排他线程，同时设置state的值
            // 若“锁没有被任何线程锁拥有”，
            // 则判断“当前线程”是不是CLH队列中的第一个线程线程，
            // 若是的话，则获取该锁，设置锁的状态，并切设置锁的拥有者为“当前线程”。
            if (c == 0) {
                // 这个 if 就是与非公平锁实现的唯一区别！！！ => 多了这个api：!hasQueuedPredecessors()
                if (!hasQueuedPredecessors() && compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            } else if (current == getExclusiveOwnerThread()) { // 如果排他线程就是当前线程，才直接设置state的值
                // 如果“独占锁”的拥有者已经为“当前线程”，
                // 则将更新锁的状态。
                int nextc = c + acquires;
                if (nextc < 0)
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }

            // 否则返回false，获取失败
            return false;
        }
    }

    public ReentrantLock() {
        sync = new NonfairSync();
    }

    public ReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
    }

    // start --------------------------------------------------------------------------->

    public boolean tryLock() {
        return sync.nonfairTryAcquire(1);
    }

    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(timeout));
    }

    public void lock() {
        sync.acquire(1);
    }

    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }

    public void unlock() {
        sync.release(1);
    }

    public Condition newCondition() {
        return sync.newCondition();
    }

    // end --------------------------------------------------------------------------->

    public int getHoldCount() {
        return sync.getHoldCount();
    }

    public boolean isHeldByCurrentThread() {
        return sync.isHeldExclusively();
    }

    public boolean isLocked() {
        return sync.isLocked();
    }

    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    protected Thread getOwner() {
        return sync.getOwner();
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
        Thread o = sync.getOwner();
        return super.toString() + ((o == null) ? "[Unlocked]" : "[Locked by thread " + o.getName() + "]");
    }
}
