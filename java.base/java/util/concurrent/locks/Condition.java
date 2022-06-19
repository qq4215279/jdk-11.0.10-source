/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent.locks;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Condition
 * @date 2022/6/19 22:36
 */
public interface Condition {

    /**
     * 阻塞
     * @date 2022/6/19 22:36
     * @param
     * @return void
     */
    void await() throws InterruptedException;

    /** 
     * 不会相应中断的阻塞
     * @date 2022/6/19 22:37
     * @param  
     * @return void
     */
    void awaitUninterruptibly();

    long awaitNanos(long nanosTimeout) throws InterruptedException;

    boolean await(long time, TimeUnit unit) throws InterruptedException;

    boolean awaitUntil(Date deadline) throws InterruptedException;

    /**
     * 唤醒
     * @date 2022/6/19 22:36
     * @param
     * @return void
     */
    void signal();

    /**
     * 唤醒所有
     * @date 2022/6/19 22:37
     * @param
     * @return void
     */
    void signalAll();
}
