/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * CountDownLatch原理和Semaphore原理类似，同样是基于AQS，不过没有公平和非公平之分。
 * 使用场景：假设一个主线程要等待5个 Worker 线程执行完才能退出，可以使用CountDownLatch来实现：
 *
 * 总结：由于是基于AQS阻塞队列来实现的，所以可以让多个线程都阻塞在state=0条件上，通过
 * countDown()一直减state，减到0后一次性唤醒所有线程。如下图所示，假设初始总数为M，N个线程
 * await()，M个线程countDown()，减到0之后，N个线程被唤醒。
 * @author liuzhen
 * @date 2022/4/16 17:49
 * @return
 */
public class CountDownLatch {

    private final Sync sync;

    /**
     *
     * @date 2022/7/15 21:52
     */
    private static final class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 4982264981922014374L;

        Sync(int count) {
            setState(count);
        }

        int getCount() {
            return getState();
        }

        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }

        /**
         *
         * @author liuzhen
         * @date 2022/4/16 17:53
         * @param releases
         * @return boolean
         */
        protected boolean tryReleaseShared(int releases) {
            // Decrement count; signal when transition to zero
            for (; ; ) {
                int c = getState();
                if (c == 0)
                    return false;
                int nextc = c - 1;
                if (compareAndSetState(c, nextc))
                    return nextc == 0;
            }
        }
    }

    public CountDownLatch(int count) {
        if (count < 0)
            throw new IllegalArgumentException("count < 0");
        this.sync = new Sync(count);
    }

    /**
     * 当前线程等待
     * @author liuzhen
     * @date 2022/4/16 17:50
     * @param
     * @return void
     */
    public void await() throws InterruptedException {
        // AQS的模板方法
        sync.acquireSharedInterruptibly(1);
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    /**
     * countDown()调用的AQS的模板方法releaseShared()，里面的tryReleaseShared(...)由CountDownLatch.Sync实现。
     * 从上面的代码可以看出，只有state=0，tryReleaseShared(...)才会返回true，然后执行doReleaseShared(...)，一次性唤醒队列中所有阻塞的线程。
     * @author liuzhen
     * @date 2022/4/16 17:52
     * @param
     * @return void
     */
    public void countDown() {
        sync.releaseShared(1);
    }

    public long getCount() {
        return sync.getCount();
    }

    public String toString() {
        return super.toString() + "[Count = " + sync.getCount() + "]";
    }
}
