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

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 该类用于协调多个线程同步执行操作的场合。
 * 使用场景：10个工程师一起来公司应聘，招聘方式分为笔试和面试。首先，要等人到齐后，开始笔试；
 * 笔试结束之后，再一起参加面试。把10个人看作10个线程，10个线程之间的同步过程
 *
 * 实现原理：CyclicBarrier基于ReentrantLock+Condition实现。
 * @author liuzhen
 * @date 2022/4/16 17:57
 * @return
 */
public class CyclicBarrier {
    private static class Generation {
        Generation() {
        }                 // prevent access constructor creation

        boolean broken;                 // initially false
    }

    private final ReentrantLock lock = new ReentrantLock();
    /** 用于线程之间相互唤醒 */
    private final Condition trip = lock.newCondition();
    /** 线程总数 */
    private final int parties;
    private final Runnable barrierCommand;
    private Generation generation = new Generation();

    private int count;

    private void nextGeneration() {
        // signal completion of last generation
        trip.signalAll();
        // set up next generation
        count = parties;
        generation = new Generation();
    }

    /**
     *
     */
    private void breakBarrier() {
        generation.broken = true;
        count = parties;
        trip.signalAll();
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/16 17:59
     * @param parties
     * @param barrierAction
     * @return
     */
    public CyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0)
            throw new IllegalArgumentException();
        // 参与方数量
        this.parties = parties;
        this.count = parties;
        // 当所有线程被唤醒时，执行barrierCommand表示的Runnable。
        this.barrierCommand = barrierAction;
    }

    public CyclicBarrier(int parties) {
        this(parties, null);
    }

    public int getParties() {
        return parties;
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/16 18:04
     * @param
     * @return int
     */
    public int await() throws InterruptedException, BrokenBarrierException {
        try {
            return dowait(false, 0L);
        } catch (TimeoutException toe) {
            throw new Error(toe); // cannot happen
        }
    }

    public int await(long timeout, TimeUnit unit) throws InterruptedException, BrokenBarrierException, TimeoutException {
        return dowait(true, unit.toNanos(timeout));
    }

    /**
     *
     * 1. CyclicBarrier是可以被重用的。以上一节的应聘场景为例，来了10个线程，这10个线程互相等待，到齐后一起被唤醒，各自执行接下来的逻辑；
     * 然后，这10个线程继续互相等待，到齐后再一起被唤醒。每一轮被称为一个Generation，就是一次同步点。
     * 2. CyclicBarrier 会响应中断。10 个线程没有到齐，如果有线程收到了中断信号，所有阻塞的线程也会被唤醒，就是上面的breakBarrier()方法。
     * 然后count被重置为初始值（parties），重新开始。
     * 3. 上面的回调方法，barrierAction只会被第10个线程执行1次（在唤醒其他9个线程之前），而不是10个线程每个都执行1次。
     * @author liuzhen
     * @date 2022/4/16 18:05
     * @param timed
     * @param nanos
     * @return int
     */
    private int dowait(boolean timed, long nanos) throws InterruptedException, BrokenBarrierException, TimeoutException {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final Generation g = generation;

            if (g.broken)
                throw new BrokenBarrierException();

            // 响应中断
            if (Thread.interrupted()) {
                // 唤醒所有阻塞的线程
                breakBarrier();
                throw new InterruptedException();
            }

            // 每个线程调用一次await()，count都要减1
            int index = --count;
            // 当count减到0的时候，此线程唤醒其他所有线程
            if (index == 0) {  // tripped
                boolean ranAction = false;
                try {
                    final Runnable command = barrierCommand;
                    if (command != null)
                        command.run();
                    ranAction = true;
                    nextGeneration();
                    return 0;
                } finally {
                    if (!ranAction)
                        breakBarrier();
                }
            }

            // loop until tripped, broken, interrupted, or timed out
            for (; ; ) {
                try {
                    if (!timed)
                        trip.await();
                    else if (nanos > 0L)
                        nanos = trip.awaitNanos(nanos);
                } catch (InterruptedException ie) {
                    if (g == generation && !g.broken) {
                        breakBarrier();
                        throw ie;
                    } else {
                        // We're about to finish waiting even if we had not
                        // been interrupted, so this interrupt is deemed to
                        // "belong" to subsequent execution.
                        Thread.currentThread().interrupt();
                    }
                }

                if (g.broken)
                    throw new BrokenBarrierException();

                if (g != generation)
                    return index;

                if (timed && nanos <= 0L) {
                    breakBarrier();
                    throw new TimeoutException();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isBroken() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return generation.broken;
        } finally {
            lock.unlock();
        }
    }

    public void reset() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            breakBarrier();   // break the current generation
            nextGeneration(); // start a new generation
        } finally {
            lock.unlock();
        }
    }

    public int getNumberWaiting() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return parties - count;
        } finally {
            lock.unlock();
        }
    }
}
