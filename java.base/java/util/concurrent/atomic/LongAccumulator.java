/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent.atomic;

import java.io.Serializable;
import java.util.function.LongBinaryOperator;

/**
 * LongAccumulator的原理和LongAdder类似，只是功能更强大
 * LongAdder只能进行累加操作，并且初始值默认为0；LongAccumulator可以自己定义一个二元操作符，并且可以传入一个初始值。
 * @author liuzhen
 * @date 2022/4/17 13:05
 */
public class LongAccumulator extends Striped64 implements Serializable {
    private static final long serialVersionUID = 7249069246863182397L;

    private final LongBinaryOperator function;
    private final long identity;

    public LongAccumulator(LongBinaryOperator accumulatorFunction, long identity) {
        this.function = accumulatorFunction;
        base = this.identity = identity;
    }

    public void accumulate(long x) {
        Cell[] cs;
        long b, v, r;
        int m;
        Cell c;
        if ((cs = cells) != null || ((r = function.applyAsLong(b = base, x)) != b && !casBase(b, r))) {
            boolean uncontended = true;
            if (cs == null || (m = cs.length - 1) < 0 || (c = cs[getProbe() & m]) == null || !(uncontended = (r = function.applyAsLong(v = c.value,
                                                                                                                                       x)) == v ||
                                                                                                             c.cas(v, r)))
                longAccumulate(x, function, uncontended);
        }
    }

    public long get() {
        Cell[] cs = cells;
        long result = base;
        if (cs != null) {
            for (Cell c : cs)
                if (c != null)
                    result = function.applyAsLong(result, c.value);
        }
        return result;
    }

    public void reset() {
        Cell[] cs = cells;
        base = identity;
        if (cs != null) {
            for (Cell c : cs)
                if (c != null)
                    c.reset(identity);
        }
    }

    public long getThenReset() {
        Cell[] cs = cells;
        long result = getAndSetBase(identity);
        if (cs != null) {
            for (Cell c : cs) {
                if (c != null) {
                    long v = c.getAndSet(identity);
                    result = function.applyAsLong(result, v);
                }
            }
        }
        return result;
    }

    public String toString() {
        return Long.toString(get());
    }

    public long longValue() {
        return get();
    }

    public int intValue() {
        return (int)get();
    }

    public float floatValue() {
        return (float)get();
    }

    public double doubleValue() {
        return (double)get();
    }

    private static class SerializationProxy implements Serializable {
        private static final long serialVersionUID = 7249069246863182397L;

        private final long value;

        private final LongBinaryOperator function;

        private final long identity;

        SerializationProxy(long value, LongBinaryOperator function, long identity) {
            this.value = value;
            this.function = function;
            this.identity = identity;
        }

        private Object readResolve() {
            LongAccumulator a = new LongAccumulator(function, identity);
            a.base = value;
            return a;
        }
    }

    private Object writeReplace() {
        return new SerializationProxy(get(), function, identity);
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.InvalidObjectException {
        throw new java.io.InvalidObjectException("Proxy required");
    }

}
