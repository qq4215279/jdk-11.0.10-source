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

package java.util.concurrent.atomic;

import java.lang.invoke.VarHandle;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

/**
 * 对于一个整数的加减操作，要保证线程安全，需要加锁，也就是加synchronized关键字。
 * 但有了Concurrent包的Atomic相关的类之后，synchronized关键字可以用AtomicInteger代替
 *
 * AtomicInteger的实现就是典型的乐观锁。除了AtomicInteger，AtomicLong也是同样的原理。
 * 悲观锁与乐观锁
 * 对于悲观锁，认为数据发生并发冲突的概率很大，读操作之前就上锁。synchronized关键字，后面要讲的ReentrantLock都是悲观锁的典型。
 * 对于乐观锁，认为数据发生并发冲突的概率比较小，读操作之前不上锁。等到写操作的时候，再判断数据在此期间是否被其他线程修改了。
 * 如果被其他线程修改了，就把数据重新读出来，重复该过程；
 * 如果没有被修改，就写回去。判断数据是否被修改，同时写回新值，这两个操作要合成一个原子操作，也就是CAS （ Compare And Set ）。
 *
 * AtomicInteger的实现就用的是“自旋”策略，如果拿不到锁，就会一直重试。
 * 自旋与阻塞
 * 当一个线程拿不到锁的时候，有以下两种基本的等待策略：
 * 策略1：放弃CPU，进入阻塞状态，等待后续被唤醒，再重新被操作系统调度。
 * 策略2：不放弃CPU，空转，不断重试，也就是所谓的“自旋”。
 * 很显然，如果是单核的CPU，只能用策略1。因为如果不放弃CPU，那么其他线程无法运行，也就无法释放锁。但对于多CPU或者多核，策略2就很有用了，因为没有线程切换的开销。
 *
 * 注意：以上两种策略并不互斥，可以结合使用。如果获取不到锁，先自旋；如果自旋还拿不到锁，再阻塞，synchronized关键字就是这样的实现策略。
 * @author liuzhen
 * @date 2022/4/16 19:08
 */
public class AtomicInteger extends Number implements java.io.Serializable {
    private static final long serialVersionUID = 6214790243416807050L;

    private static final jdk.internal.misc.Unsafe U = jdk.internal.misc.Unsafe.getUnsafe();

    /**
     * objectFieldOffset(...) 方法调用，就是为了找到AtomicInteger类中value属性所在的内存偏移量。
     */
    private static final long VALUE = U.objectFieldOffset(AtomicInteger.class, "value");

    private volatile int value;

    public AtomicInteger(int initialValue) {
        value = initialValue;
    }

    public AtomicInteger() {
    }

    public final int get() {
        return value;
    }

    public final void set(int newValue) {
        value = newValue;
    }

    public final void lazySet(int newValue) {
        U.putIntRelease(this, VALUE, newValue);
    }

    public final int getAndSet(int newValue) {
        return U.getAndSetInt(this, VALUE, newValue);
    }

    public final boolean compareAndSet(int expectedValue, int newValue) {
        return U.compareAndSetInt(this, VALUE, expectedValue, newValue);
    }

    @Deprecated(since = "9")
    public final boolean weakCompareAndSet(int expectedValue, int newValue) {
        return U.weakCompareAndSetIntPlain(this, VALUE, expectedValue, newValue);
    }

    public final boolean weakCompareAndSetPlain(int expectedValue, int newValue) {
        return U.weakCompareAndSetIntPlain(this, VALUE, expectedValue, newValue);
    }

    /**
     * 加
     * @author liuzhen
     * @date 2022/4/16 19:09
     * @param
     * @return int
     */
    public final int getAndIncrement() {
        return U.getAndAddInt(this, VALUE, 1);
    }

    /**
     * 减
     * @author liuzhen
     * @date 2022/4/16 19:09
     * @param
     * @return int
     */
    public final int getAndDecrement() {
        return U.getAndAddInt(this, VALUE, -1);
    }

    public final int getAndAdd(int delta) {
        return U.getAndAddInt(this, VALUE, delta);
    }

    public final int incrementAndGet() {
        return U.getAndAddInt(this, VALUE, 1) + 1;
    }

    public final int decrementAndGet() {
        return U.getAndAddInt(this, VALUE, -1) - 1;
    }

    public final int addAndGet(int delta) {
        return U.getAndAddInt(this, VALUE, delta) + delta;
    }

    public final int getAndUpdate(IntUnaryOperator updateFunction) {
        int prev = get(), next = 0;
        for (boolean haveNext = false; ; ) {
            if (!haveNext)
                next = updateFunction.applyAsInt(prev);
            if (weakCompareAndSetVolatile(prev, next))
                return prev;
            haveNext = (prev == (prev = get()));
        }
    }

    public final int updateAndGet(IntUnaryOperator updateFunction) {
        int prev = get(), next = 0;
        for (boolean haveNext = false; ; ) {
            if (!haveNext)
                next = updateFunction.applyAsInt(prev);
            if (weakCompareAndSetVolatile(prev, next))
                return next;
            haveNext = (prev == (prev = get()));
        }
    }

    public final int getAndAccumulate(int x, IntBinaryOperator accumulatorFunction) {
        int prev = get(), next = 0;
        for (boolean haveNext = false; ; ) {
            if (!haveNext)
                next = accumulatorFunction.applyAsInt(prev, x);
            if (weakCompareAndSetVolatile(prev, next))
                return prev;
            haveNext = (prev == (prev = get()));
        }
    }

    public final int accumulateAndGet(int x, IntBinaryOperator accumulatorFunction) {
        int prev = get(), next = 0;
        for (boolean haveNext = false; ; ) {
            if (!haveNext)
                next = accumulatorFunction.applyAsInt(prev, x);
            if (weakCompareAndSetVolatile(prev, next))
                return next;
            haveNext = (prev == (prev = get()));
        }
    }

    public String toString() {
        return Integer.toString(get());
    }

    public int intValue() {
        return get();
    }

    public long longValue() {
        return (long)get();
    }

    public float floatValue() {
        return (float)get();
    }

    public double doubleValue() {
        return (double)get();
    }

    // jdk9

    public final int getPlain() {
        return U.getInt(this, VALUE);
    }

    public final void setPlain(int newValue) {
        U.putInt(this, VALUE, newValue);
    }

    public final int getOpaque() {
        return U.getIntOpaque(this, VALUE);
    }

    public final void setOpaque(int newValue) {
        U.putIntOpaque(this, VALUE, newValue);
    }

    public final int getAcquire() {
        return U.getIntAcquire(this, VALUE);
    }

    public final void setRelease(int newValue) {
        U.putIntRelease(this, VALUE, newValue);
    }

    public final int compareAndExchange(int expectedValue, int newValue) {
        return U.compareAndExchangeInt(this, VALUE, expectedValue, newValue);
    }

    public final int compareAndExchangeAcquire(int expectedValue, int newValue) {
        return U.compareAndExchangeIntAcquire(this, VALUE, expectedValue, newValue);
    }

    public final int compareAndExchangeRelease(int expectedValue, int newValue) {
        return U.compareAndExchangeIntRelease(this, VALUE, expectedValue, newValue);
    }

    public final boolean weakCompareAndSetVolatile(int expectedValue, int newValue) {
        return U.weakCompareAndSetInt(this, VALUE, expectedValue, newValue);
    }

    public final boolean weakCompareAndSetAcquire(int expectedValue, int newValue) {
        return U.weakCompareAndSetIntAcquire(this, VALUE, expectedValue, newValue);
    }

    public final boolean weakCompareAndSetRelease(int expectedValue, int newValue) {
        return U.weakCompareAndSetIntRelease(this, VALUE, expectedValue, newValue);
    }

}
