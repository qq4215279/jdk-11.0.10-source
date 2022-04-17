/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent.atomic;

import java.io.Serializable;

/**
 * LongAdder原理
 * AtomicLong内部是一个volatile long型变量，由多个线程对这个变量进行CAS操作。多个线程同时对一个变量进行CAS操作，在高并发的场景下仍不够快，
 * 如果再要提高性能，该怎么做呢？
 * 把一个变量拆成多份，变为多个变量，有些类似于 ConcurrentHashMap 的分段锁的例子。如下图所示，把一个Long型拆成一个base变量外加多个Cell，每个Cell包装了一个Long型变量。
 * 当多个线程并发累加的时候，如果并发度低，就直接加到base变量上；如果并发度高，冲突大，平摊到这些Cell上。在最后取值的时候，再把base和这些Cell求sum运算。
 *
 * 由于无论是long，还是double，都是64位的。但因为没有double型的CAS操作，所以是通过把double型转化成long型来实现的。
 * 所以，上面的base和cell[]变量，是位于基类Striped64当中的。英文Striped意为“条带”，也就是分片。
 * @author liuzhen
 * @date 2022/4/17 12:02
 */
public class LongAdder extends Striped64 implements Serializable {
    private static final long serialVersionUID = 7249069246863182397L;

    public LongAdder() {
    }

    /**
     * 核心方法
     * 当一个线程调用add(x)的时候，首先会尝试使用casBase把x加到base变量上。如果不成功，则再用c.cas(...)方法尝试把 x 加到 Cell 数组的某个元素上。
     * 如果还不成功，最后再调用longAccumulate(...)方法。
     *
     * 注意：Cell[]数组的大小始终是2的整数次方，在运行中会不断扩容，每次扩容都是增长2倍。上面代码中的 cs[getProbe() & m] 其实就是对数组的大小取模。
     * 因为m=cs.length–1，getProbe()为该线程生成一个随机数，用该随机数对数组的长度取模。因为数组长度是2的整数次方，所以可以用&操作来优化取模运算。
     * 对于一个线程来说，它并不在意到底是把x累加到base上面，还是累加到Cell[]数组上面，只要累加成功就可以。因此，这里使用随机数来实现Cell的长度取模。
     *
     * 如果两次尝试都不成功，则调用 longAccumulate(...)方法，该方法在 Striped64 里面LongAccumulator也会用到
     * @author liuzhen
     * @date 2022/4/17 12:57
     * @param x
     * @return void
     */
    public void add(long x) {
        Cell[] cs;
        long b, v;
        int m;
        Cell c;
        // 第一次尝试
        if ((cs = cells) != null || !casBase(b = base, b + x)) {
            boolean uncontended = true;
            // 第二次尝试
            if (cs == null || (m = cs.length - 1) < 0 || (c = cs[getProbe() & m]) == null || !(uncontended = c.cas(v = c.value, v + x)))
                longAccumulate(x, null, uncontended);
        }
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/17 12:57
     * @param
     * @return void
     */
    public void increment() {
        add(1L);
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/17 12:57
     * @param
     * @return void
     */
    public void decrement() {
        add(-1L);
    }

    /**
     * sum运算。
     * @author liuzhen
     * @date 2022/4/17 12:04
     * @param
     * @return long
     */
    public long sum() {
        Cell[] cs = cells;
        long sum = base;
        if (cs != null) {
            for (Cell c : cs)
                if (c != null)
                    sum += c.value;
        }
        return sum;
    }

    public void reset() {
        Cell[] cs = cells;
        base = 0L;
        if (cs != null) {
            for (Cell c : cs)
                if (c != null)
                    c.reset();
        }
    }

    public long sumThenReset() {
        Cell[] cs = cells;
        long sum = getAndSetBase(0L);
        if (cs != null) {
            for (Cell c : cs) {
                if (c != null)
                    sum += c.getAndSet(0L);
            }
        }
        return sum;
    }

    public String toString() {
        return Long.toString(sum());
    }

    public long longValue() {
        return sum();
    }

    public int intValue() {
        return (int)sum();
    }

    public float floatValue() {
        return (float)sum();
    }

    public double doubleValue() {
        return (double)sum();
    }

    private static class SerializationProxy implements Serializable {
        private static final long serialVersionUID = 7249069246863182397L;

        /**
         * The current value returned by sum().
         *
         * @serial
         */
        private final long value;

        SerializationProxy(LongAdder a) {
            value = a.sum();
        }

        /**
         * Returns a {@code LongAdder} object with initial state
         * held by this proxy.
         *
         * @return a {@code LongAdder} object with initial state
         * held by this proxy
         */
        private Object readResolve() {
            LongAdder a = new LongAdder();
            a.base = value;
            return a;
        }
    }

    private Object writeReplace() {
        return new SerializationProxy(this);
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.InvalidObjectException {
        throw new java.io.InvalidObjectException("Proxy required");
    }

}
