/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent.atomic;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * ABA问题与解决办法
 * 到目前为止，CAS都是基于“值”来做比较的。但如果另外一个线程把变量的值从A改为B，再从B改回到A，那么尽管修改过两次，可是在当前线程做CAS操作的时候，
 * 却会因为值没变而认为数据没有被其他线程修改过，这就是所谓的ABA问题。
 * 要解决 ABA 问题，不仅要比较“值”，还要比较“版本号”，而这正是 AtomicStampedReference做的事情，其对应的CAS方法如下：
 *
 * 要解决Integer或者Long型变量的ABA问题，为什么只有AtomicStampedReference，而没有AtomicStampedInteger或者AtomictStampedLong呢？
 * 因为这里要同时比较数据的“值”和“版本号”，而Integer型或者Long型的CAS没有办法同时比较两个变量。
 * 于是只能把值和版本号封装成一个对象，也就是这里面的Pair内部类，然后通过对象引用的CAS来实现。
 * @author liuzhen
 * @date 2022/4/16 22:53
 */
public class AtomicStampedReference<V> {

    /**
     * 把值和版本号封装成一个对象，也就是这里面的Pair内部类，然后通过对象引用的CAS来实现。
     * @param <T>
     */
    private static class Pair<T> {
        final T reference;
        final int stamp;

        private Pair(T reference, int stamp) {
            this.reference = reference;
            this.stamp = stamp;
        }

        static <T> Pair<T> of(T reference, int stamp) {
            return new Pair<T>(reference, stamp);
        }
    }

    private volatile Pair<V> pair;

    /**
     * 当使用的时候，在构造方法里面传入值和版本号两个参数，应用程序对版本号进行累加操作，然后调用上面的CAS。
     * @author liuzhen
     * @date 2022/4/16 22:57
     * @param initialRef
     * @param initialStamp
     * @return
     */
    public AtomicStampedReference(V initialRef, int initialStamp) {
        pair = Pair.of(initialRef, initialStamp);
    }

    public V getReference() {
        return pair.reference;
    }

    public int getStamp() {
        return pair.stamp;
    }

    public V get(int[] stampHolder) {
        Pair<V> pair = this.pair;
        stampHolder[0] = pair.stamp;
        return pair.reference;
    }

    public boolean weakCompareAndSet(V expectedReference, V newReference, int expectedStamp, int newStamp) {
        return compareAndSet(expectedReference, newReference, expectedStamp, newStamp);
    }

    /**
     *
     * @date 2022/7/20 7:04
     * @param expectedReference 预期引用
     * @param newReference 更新后的引用
     * @param expectedStamp 预期标志
     * @param newStamp 更新后的标志
     * @return boolean
     */
    public boolean compareAndSet(V expectedReference, V newReference, int expectedStamp, int newStamp) {
        Pair<V> current = pair;
        return expectedReference == current.reference && expectedStamp == current.stamp &&
               ((newReference == current.reference && newStamp == current.stamp) || casPair(current, Pair.of(newReference, newStamp)));
    }

    public void set(V newReference, int newStamp) {
        Pair<V> current = pair;
        if (newReference != current.reference || newStamp != current.stamp)
            this.pair = Pair.of(newReference, newStamp);
    }

    public boolean attemptStamp(V expectedReference, int newStamp) {
        Pair<V> current = pair;
        return expectedReference == current.reference && (newStamp == current.stamp || casPair(current, Pair.of(expectedReference, newStamp)));
    }

    // VarHandle mechanics
    private static final VarHandle PAIR;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            PAIR = l.findVarHandle(AtomicStampedReference.class, "pair", Pair.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private boolean casPair(Pair<V> cmp, Pair<V> val) {
        return PAIR.compareAndSet(this, cmp, val);
    }
}
