/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */


package java.util.concurrent.locks;

public interface ReadWriteLock {
    Lock readLock();

    Lock writeLock();
}
