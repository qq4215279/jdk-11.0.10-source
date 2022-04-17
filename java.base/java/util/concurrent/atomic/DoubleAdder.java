/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent.atomic;

import java.io.Serializable;

public class DoubleAdder extends Striped64 implements Serializable {
    private static final long serialVersionUID = 7249069246863182397L;

    public DoubleAdder() {
    }

    public void add(double x) {
        Cell[] cs;
        long b, v;
        int m;
        Cell c;
        if ((cs = cells) != null || !casBase(b = base, Double.doubleToRawLongBits(Double.longBitsToDouble(b) + x))) {
            boolean uncontended = true;
            if (cs == null || (m = cs.length - 1) < 0 || (c = cs[getProbe() & m]) == null || !(uncontended = c.cas(v = c.value, Double
                .doubleToRawLongBits(Double.longBitsToDouble(v) + x))))
                doubleAccumulate(x, null, uncontended);
        }
    }

    public double sum() {
        Cell[] cs = cells;
        double sum = Double.longBitsToDouble(base);
        if (cs != null) {
            for (Cell c : cs)
                if (c != null)
                    sum += Double.longBitsToDouble(c.value);
        }
        return sum;
    }

    public void reset() {
        Cell[] cs = cells;
        base = 0L; // relies on fact that double 0 must have same rep as long
        if (cs != null) {
            for (Cell c : cs)
                if (c != null)
                    c.reset();
        }
    }

    public double sumThenReset() {
        Cell[] cs = cells;
        double sum = Double.longBitsToDouble(getAndSetBase(0L));
        if (cs != null) {
            for (Cell c : cs) {
                if (c != null)
                    sum += Double.longBitsToDouble(c.getAndSet(0L));
            }
        }
        return sum;
    }

    public String toString() {
        return Double.toString(sum());
    }

    public double doubleValue() {
        return sum();
    }

    public long longValue() {
        return (long)sum();
    }

    public int intValue() {
        return (int)sum();
    }

    public float floatValue() {
        return (float)sum();
    }

    private static class SerializationProxy implements Serializable {
        private static final long serialVersionUID = 7249069246863182397L;

        /**
         * The current value returned by sum().
         *
         * @serial
         */
        private final double value;

        SerializationProxy(DoubleAdder a) {
            value = a.sum();
        }

        /**
         * Returns a {@code DoubleAdder} object with initial state
         * held by this proxy.
         *
         * @return a {@code DoubleAdder} object with initial state
         * held by this proxy
         */
        private Object readResolve() {
            DoubleAdder a = new DoubleAdder();
            a.base = Double.doubleToRawLongBits(value);
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
