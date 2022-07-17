/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */


package java.util.concurrent.locks;

public interface ReadWriteLock {

    /**
     * 获取读锁
     * @date 2022/7/13 20:54
     * @param
     * @return java.util.concurrent.locks.Lock
     */
    Lock readLock();

    /**
     * 获取写锁
     * @date 2022/7/13 20:54
     * @param
     * @return java.util.concurrent.locks.Lock
     */
    Lock writeLock();

}
