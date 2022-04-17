/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent.atomic;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * 为什么需要AtomicBoolean
 * 对于int或者long型变量，需要进行加减操作，所以要加锁；
 * 但对于一个boolean类型来说，true或false的赋值和取值操作，加上volatile关键字就够了，为什么还需要AtomicBoolean呢？
 * 这是因为往往要实现下面这种功能：if (!flag) { flag = true; // ... }
 * 也就是要实现 compare和set两个操作合在一起的原子性，而这也正是CAS提供的功能。上面的代码，就变成：if (compareAndSet(false, true)) { // ... }
 * 也就是要实现 compare和set两个操作合在一起的原子性，而这也正是CAS提供的功能。上面的代码，
 * @author liuzhen
 * @date 2022/4/16 19:29
 */
public class AtomicBoolean implements java.io.Serializable {
    private static final long serialVersionUID = 4654671469794556979L;
    private static final VarHandle VALUE;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            VALUE = l.findVarHandle(AtomicBoolean.class, "value", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private volatile int value;

    public AtomicBoolean(boolean initialValue) {
        value = initialValue ? 1 : 0;
    }

    public AtomicBoolean() {
    }

    public final boolean get() {
        return value != 0;
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/16 19:35
     * @param expectedValue
     * @param newValue
     * @return boolean
     */
    public final boolean compareAndSet(boolean expectedValue, boolean newValue) {
        // 对于用int型来代替的，在入参的时候，将boolean类型转换成int类型；在返回值的时候，将int类型转换成boolean类型。
        return VALUE.compareAndSet(this, (expectedValue ? 1 : 0), (newValue ? 1 : 0));
    }

    @Deprecated(since = "9")
    public boolean weakCompareAndSet(boolean expectedValue, boolean newValue) {
        return VALUE.weakCompareAndSetPlain(this, (expectedValue ? 1 : 0), (newValue ? 1 : 0));
    }

    public boolean weakCompareAndSetPlain(boolean expectedValue, boolean newValue) {
        return VALUE.weakCompareAndSetPlain(this, (expectedValue ? 1 : 0), (newValue ? 1 : 0));
    }

    public final void set(boolean newValue) {
        value = newValue ? 1 : 0;
    }

    public final void lazySet(boolean newValue) {
        VALUE.setRelease(this, (newValue ? 1 : 0));
    }

    public final boolean getAndSet(boolean newValue) {
        return (int)VALUE.getAndSet(this, (newValue ? 1 : 0)) != 0;
    }

    public String toString() {
        return Boolean.toString(get());
    }

    // jdk9

    public final boolean getPlain() {
        return (int)VALUE.get(this) != 0;
    }

    public final void setPlain(boolean newValue) {
        VALUE.set(this, newValue ? 1 : 0);
    }

    public final boolean getOpaque() {
        return (int)VALUE.getOpaque(this) != 0;
    }

    public final void setOpaque(boolean newValue) {
        VALUE.setOpaque(this, newValue ? 1 : 0);
    }

    public final boolean getAcquire() {
        return (int)VALUE.getAcquire(this) != 0;
    }

    public final void setRelease(boolean newValue) {
        VALUE.setRelease(this, newValue ? 1 : 0);
    }

    public final boolean compareAndExchange(boolean expectedValue, boolean newValue) {
        return (int)VALUE.compareAndExchange(this, (expectedValue ? 1 : 0), (newValue ? 1 : 0)) != 0;
    }

    public final boolean compareAndExchangeAcquire(boolean expectedValue, boolean newValue) {
        return (int)VALUE.compareAndExchangeAcquire(this, (expectedValue ? 1 : 0), (newValue ? 1 : 0)) != 0;
    }

    public final boolean compareAndExchangeRelease(boolean expectedValue, boolean newValue) {
        return (int)VALUE.compareAndExchangeRelease(this, (expectedValue ? 1 : 0), (newValue ? 1 : 0)) != 0;
    }

    public final boolean weakCompareAndSetVolatile(boolean expectedValue, boolean newValue) {
        return VALUE.weakCompareAndSet(this, (expectedValue ? 1 : 0), (newValue ? 1 : 0));
    }

    public final boolean weakCompareAndSetAcquire(boolean expectedValue, boolean newValue) {
        return VALUE.weakCompareAndSetAcquire(this, (expectedValue ? 1 : 0), (newValue ? 1 : 0));
    }

    public final boolean weakCompareAndSetRelease(boolean expectedValue, boolean newValue) {
        return VALUE.weakCompareAndSetRelease(this, (expectedValue ? 1 : 0), (newValue ? 1 : 0));
    }

}
