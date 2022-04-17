/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

/** 
 *
 * @author liuzhen
 * @date 2022/4/16 17:48
 * @param null 
 * @return 
 */
package java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;

/**
 * 锁
 * 常用的方法是lock()/unlock()。lock()不能被中断，对应的lockInterruptibly()可以被中断。
 * ReentrantLock本身没有代码逻辑，实现都在其内部类Sync中：
 * @author liuzhen
 * @date 2022/4/17 13:13
 */
public interface Lock {

    void lock();

    void lockInterruptibly() throws InterruptedException;

    boolean tryLock();

    boolean tryLock(long time, TimeUnit unit) throws InterruptedException;

    void unlock();

    Condition newCondition();
}
