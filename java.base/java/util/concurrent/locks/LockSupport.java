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

package java.util.concurrent.locks;

import jdk.internal.misc.Unsafe;

/**
 * 在当前线程中调用park()，该线程就会被阻塞；在另外一个线程中，调用unpark(Threadthread)，传入一个被阻塞的线程，就可以唤醒阻塞在park()地方的线程。
 * unpark(Thread thread)，它实现了一个线程对另外一个线程的“精准唤醒”。notify也只是唤醒某一个线程，但无法指定具体唤醒哪个线程。
 * @author liuzhen
 * @date 2022/4/17 17:28
 */
public class LockSupport {
    // Hotspot implementation via intrinsics API
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long PARKBLOCKER = U.objectFieldOffset(Thread.class, "parkBlocker");
    private static final long SECONDARY = U.objectFieldOffset(Thread.class, "threadLocalRandomSecondarySeed");
    private static final long TID = U.objectFieldOffset(Thread.class, "tid");


    private LockSupport() {
    } // Cannot be instantiated.

    private static void setBlocker(Thread t, Object arg) {
        // Even though volatile, hotspot doesn't need a write barrier here.
        U.putObject(t, PARKBLOCKER, arg);
    }

    /**
     * 阻塞或唤醒线程的一对操作原语，也就是park/unpark。
     * 在当前线程中调用park()，该线程就会被阻塞
     * @author liuzhen
     * @date 2022/4/17 17:27
     * @param
     * @return void
     */
    public static void park() {
        U.park(false, 0L);
    }

    /**
     * 阻塞或唤醒线程的一对操作原语，也就是park/unpark。
     * 在另外一个线程中，调用unpark(Thread thread)，传入一个被阻塞的线程，就可以唤醒阻塞在park()地方的线程。
     * unpark(Thread thread)，它实现了一个线程对另外一个线程的“精准唤醒”。notify也只是唤醒某一个线程，但无法指定具体唤醒哪个线程。
     * @author liuzhen
     * @date 2022/4/17 17:26
     * @param thread
     * @return void
     */
    public static void unpark(Thread thread) {
        if (thread != null)
            U.unpark(thread);
    }

    public static void park(Object blocker) {
        Thread t = Thread.currentThread();
        setBlocker(t, blocker);
        U.park(false, 0L);
        setBlocker(t, null);
    }

    public static void parkNanos(Object blocker, long nanos) {
        if (nanos > 0) {
            Thread t = Thread.currentThread();
            setBlocker(t, blocker);
            U.park(false, nanos);
            setBlocker(t, null);
        }
    }

    public static void parkUntil(Object blocker, long deadline) {
        Thread t = Thread.currentThread();
        setBlocker(t, blocker);
        U.park(true, deadline);
        setBlocker(t, null);
    }

    public static Object getBlocker(Thread t) {
        if (t == null)
            throw new NullPointerException();
        return U.getObjectVolatile(t, PARKBLOCKER);
    }

    public static void parkNanos(long nanos) {
        if (nanos > 0)
            U.park(false, nanos);
    }

    public static void parkUntil(long deadline) {
        U.park(true, deadline);
    }

    static final int nextSecondarySeed() {
        int r;
        Thread t = Thread.currentThread();
        if ((r = U.getInt(t, SECONDARY)) != 0) {
            r ^= r << 13;   // xorshift
            r ^= r >>> 17;
            r ^= r << 5;
        } else if ((r = java.util.concurrent.ThreadLocalRandom.current().nextInt()) == 0)
            r = 1; // avoid zero
        U.putInt(t, SECONDARY, r);
        return r;
    }

    static final long getThreadId(Thread thread) {
        return U.getLong(thread, TID);
    }

}
