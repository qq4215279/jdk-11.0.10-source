/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent.atomic;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleBinaryOperator;
import java.util.function.LongBinaryOperator;

/**
 * 从JDK 8开始，针对 Long 型的原子操作，Java又提供了LongAdder、LongAccumulator；
 * 针对 Double 类型，Java提供了DoubleAdder、DoubleAccumulator。
 * Striped64相关的类的继承层次如下：LongAdder  LongAccumulator  DoubleAdder  DoubleAccumulator
 * @author liuzhen
 * @date 2022/4/17 11:59
 */
@SuppressWarnings("serial")
abstract class Striped64 extends Number {
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    transient volatile Cell[] cells;

    transient volatile long base;

    transient volatile int cellsBusy;

    @jdk.internal.vm.annotation.Contended
    static final class Cell {
        volatile long value;

        Cell(long x) {
            value = x;
        }

        final boolean cas(long cmp, long val) {
            return VALUE.compareAndSet(this, cmp, val);
        }

        final void reset() {
            VALUE.setVolatile(this, 0L);
        }

        final void reset(long identity) {
            VALUE.setVolatile(this, identity);
        }

        final long getAndSet(long val) {
            return (long)VALUE.getAndSet(this, val);
        }

        // VarHandle mechanics
        private static final VarHandle VALUE;

        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                VALUE = l.findVarHandle(Cell.class, "value", long.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    Striped64() {
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/17 13:00
     * @param x
     * @param fn
     * @param wasUncontended
     * @return void
     */
    final void longAccumulate(long x, LongBinaryOperator fn, boolean wasUncontended) {
        int h;
        if ((h = getProbe()) == 0) {
            ThreadLocalRandom.current(); // force initialization
            h = getProbe();
            wasUncontended = true;
        }
        // true表示最后一个slot非空
        boolean collide = false;                // True if last slot nonempty
        done:
        for (; ; ) {
            Cell[] cs;
            Cell c;
            int n;
            long v;
            // 如果cells不是null，且cells长度大于0
            if ((cs = cells) != null && (n = cs.length) > 0) {
                // cells最大下标对随机数取模，得到新下标。
                // 如果此新下标处的元素是null
                if ((c = cs[(n - 1) & h]) == null) {
                    // 自旋锁标识，用于创建cells或扩容cells
                    if (cellsBusy == 0) {       // Try to attach new Cell
                        // 尝试添加新的Cell
                        Cell r = new Cell(x);   // Optimistically create
                        // 如果cellsBusy为0，则CAS操作cellsBusy为1，获取锁
                        if (cellsBusy == 0 && casCellsBusy()) {
                            // 获取锁之后，再次检查
                            try {               // Recheck under lock
                                Cell[] rs;
                                int m, j;
                                if ((rs = cells) != null && (m = rs.length) > 0 && rs[j = (m - 1) & h] == null) {
                                    // 赋值成功，返回
                                    rs[j] = r;
                                    break done;
                                }
                            } finally {
                                // 重置标志位，释放锁
                                cellsBusy = 0;
                            }
                            // 如果slot非空，则进入下一次循环
                            continue;           // Slot is now non-empty
                        }
                    }
                    collide = false;
                } else if (!wasUncontended) // CAS操作失败      // CAS already known to fail
                    // rehash之后继续
                    wasUncontended = true;      // Continue after rehash
                else if (c.cas(v = c.value, (fn == null) ? v + x : fn.applyAsLong(v, x)))
                    break;
                else if (n >= NCPU || cells != cs)
                    collide = false;            // At max size or stale
                else if (!collide)
                    collide = true;
                else if (cellsBusy == 0 && casCellsBusy()) {
                    // 扩容，每次都是上次的两倍长度
                    try {
                        if (cells == cs)        // Expand table unless stale
                            cells = Arrays.copyOf(cs, n << 1);
                    } finally {
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                h = advanceProbe(h);
            }
            // 如果cells为null或者cells的长度为0，则需要初始化cells数组
            // 此时需要加锁，进行CAS操作
            else if (cellsBusy == 0 && cells == cs && casCellsBusy()) {
                try {                           // Initialize table
                    if (cells == cs) {
                        // 实例化Cell数组，实例化Cell，保存x值
                        Cell[] rs = new Cell[2];
                        // h为随机数，对Cells数组取模，赋值新的Cell对象。
                        rs[h & 1] = new Cell(x);
                        cells = rs;
                        break done;
                    }
                } finally {
                    // 释放CAS锁
                    cellsBusy = 0;
                }
            }
            // Fall back on using base
            // 如果CAS操作失败，最后回到对base的操作
            // 判断fn是否为null，如果是null则执行加操作，否则执行fn提供的操作
            // 如果操作失败，则重试for循环流程，成功就退出循环
            else if (casBase(v = base, (fn == null) ? v + x : fn.applyAsLong(v, x)))
                break done;
        }
    }

    /**
     *
     * @date 2022/7/24 15:31
     * @param x
     * @param fn
     * @param wasUncontended
     * @return void
     */
    final void doubleAccumulate(double x, DoubleBinaryOperator fn, boolean wasUncontended) {
        int h;
        if ((h = getProbe()) == 0) {
            ThreadLocalRandom.current(); // force initialization
            h = getProbe();
            wasUncontended = true;
        }
        boolean collide = false;                // True if last slot nonempty
        done:
        for (; ; ) {
            Cell[] cs;
            Cell c;
            int n;
            long v;
            if ((cs = cells) != null && (n = cs.length) > 0) {
                if ((c = cs[(n - 1) & h]) == null) {
                    if (cellsBusy == 0) {       // Try to attach new Cell
                        Cell r = new Cell(Double.doubleToRawLongBits(x));
                        if (cellsBusy == 0 && casCellsBusy()) {
                            try {               // Recheck under lock
                                Cell[] rs;
                                int m, j;
                                if ((rs = cells) != null && (m = rs.length) > 0 && rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    break done;
                                }
                            } finally {
                                cellsBusy = 0;
                            }
                            continue;           // Slot is now non-empty
                        }
                    }
                    collide = false;
                } else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash
                else if (c.cas(v = c.value, apply(fn, v, x)))
                    break;
                else if (n >= NCPU || cells != cs)
                    collide = false;            // At max size or stale
                else if (!collide)
                    collide = true;
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        if (cells == cs)        // Expand table unless stale
                            cells = Arrays.copyOf(cs, n << 1);
                    } finally {
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                h = advanceProbe(h);
            } else if (cellsBusy == 0 && cells == cs && casCellsBusy()) {
                try {                           // Initialize table
                    if (cells == cs) {
                        Cell[] rs = new Cell[2];
                        rs[h & 1] = new Cell(Double.doubleToRawLongBits(x));
                        cells = rs;
                        break done;
                    }
                } finally {
                    cellsBusy = 0;
                }
            }
            // Fall back on using base
            else if (casBase(v = base, apply(fn, v, x)))
                break done;
        }
    }

    final boolean casBase(long cmp, long val) {
        return BASE.compareAndSet(this, cmp, val);
    }

    final long getAndSetBase(long val) {
        return (long)BASE.getAndSet(this, val);
    }

    final boolean casCellsBusy() {
        return CELLSBUSY.compareAndSet(this, 0, 1);
    }

    static final int getProbe() {
        return (int)THREAD_PROBE.get(Thread.currentThread());
    }

    static final int advanceProbe(int probe) {
        probe ^= probe << 13;   // xorshift
        probe ^= probe >>> 17;
        probe ^= probe << 5;
        THREAD_PROBE.set(Thread.currentThread(), probe);
        return probe;
    }

    private static long apply(DoubleBinaryOperator fn, long v, double x) {
        double d = Double.longBitsToDouble(v);
        d = (fn == null) ? d + x : fn.applyAsDouble(d, x);
        return Double.doubleToRawLongBits(d);
    }

    // VarHandle mechanics
    private static final VarHandle BASE;
    private static final VarHandle CELLSBUSY;
    private static final VarHandle THREAD_PROBE;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            BASE = l.findVarHandle(Striped64.class, "base", long.class);
            CELLSBUSY = l.findVarHandle(Striped64.class, "cellsBusy", int.class);
            l = java.security.AccessController.doPrivileged(new java.security.PrivilegedAction<>() {
                public MethodHandles.Lookup run() {
                    try {
                        return MethodHandles.privateLookupIn(Thread.class, MethodHandles.lookup());
                    } catch (ReflectiveOperationException e) {
                        throw new ExceptionInInitializerError(e);
                    }
                }
            });
            THREAD_PROBE = l.findVarHandle(Thread.class, "threadLocalRandomProbe", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

}
