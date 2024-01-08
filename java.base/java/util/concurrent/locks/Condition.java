/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent.locks;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Condition
 * Condition是 java.util.concurrent.locks路径下的一个接口，Condition与重入锁搭配使用的，它的创建使用重入锁的 newCondition() 方法来完成。
 * 它是对重入锁的控制的一种条件补充，通过线程的等待和唤醒让线程控制更加地灵活，Condition接口的的await()和signal()方法就像Object.wait()和Object.notify()一样。
 * @date 2022/6/19 22:36
 */
public interface Condition {

    /**
     * 阻塞 会使当前线程等待。当其他线程中使用signal或者signalAll()方法时，线程会重新获得锁，并继续执行，或者当线程被中断时，也能跳出等待。
     * @date 2022/6/19 22:36
     * @param
     * @return void
     */
    void await() throws InterruptedException;

    /** 
     * 不会相应中断的阻塞 该方法与await()方法基本相同，但是它不会再等待过程中响应中断
     * @date 2022/6/19 22:37
     * @param  
     * @return void
     */
    void awaitUninterruptibly();

    /**
     * nanosTimeout是该方法等待信号的最大时间，如果一定时间内收到signal或者signalAll信号，那么就返回一个long类型的值，值为 nanosTimeout 减去这段等待时间。
     * 如果等待过程中收到了中断异常，就会响应中断。如果直至nanosTimeout时间耗完仍未收到信号，则会返回0或者负数
     * @return long
     * @date 2024/1/8 16:14
     */
    long awaitNanos(long nanosTimeout) throws InterruptedException;

    /**
     * 等待一段时间，如果等待的时间内收到了signal或者signalAll就返回true,否则返回false。或者响应中断
     * @param time time
     * @param unit unit
     * @return boolean
     * @date 2024/1/8 16:15
     */
    boolean await(long time, TimeUnit unit) throws InterruptedException;

    /**
     * 等待一个时刻，如果返回时到达了最后期限就返回false否则就是true
     * @param deadline deadline
     * @return boolean
     * @date 2024/1/8 16:15
     */
    boolean awaitUntil(Date deadline) throws InterruptedException;

    /**
     * 唤醒一个在await() 队列中的线程
     * @date 2022/6/19 22:36
     * @param
     * @return void
     */
    void signal();

    /**
     * 唤醒等待队列中的所有线程
     * @date 2022/6/19 22:37
     * @param
     * @return void
     */
    void signalAll();
}
