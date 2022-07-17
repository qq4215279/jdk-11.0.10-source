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

    /**
     * 尝试获取锁
     * @date 2022/7/13 20:53
     * @param
     * @return boolean
     */
    boolean tryLock();

    boolean tryLock(long time, TimeUnit unit) throws InterruptedException;

    /**
     * 不能被中断
     * @date 2022/6/18 18:43
     * @param
     * @return void
     */
    void lock();

    /**
     * 可以被中断
     * @date 2022/6/18 18:44
     * @param
     * @return void
     */
    void lockInterruptibly() throws InterruptedException;

    /** 
     * 不能被中断
     * @date 2022/6/18 18:44 
     * @param  
     * @return void
     */
    void unlock();

    Condition newCondition();
}
