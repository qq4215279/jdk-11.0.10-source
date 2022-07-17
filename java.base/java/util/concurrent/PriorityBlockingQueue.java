/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */


package java.util.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractQueue;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

import jdk.internal.access.SharedSecrets;

/**
 * PriorityBlockingQueue
 * 队列通常是先进先出的，而PriorityQueue是按照元素的优先级从小到大出队列的。
 * 正因为如此，PriorityQueue中的2个元素之间需要可以比较大小，并实现Comparable接口。
 *
 * 在阻塞的实现方面，和ArrayBlockingQueue的机制相似，主要区别是用数组实现了一个二叉堆，从而实现按优先级从小到大出队列。
 * 另一个区别是没有notFull条件，当元素个数超出数组长度时，执行扩容操作。
 * @date 2022/7/16 15:12
 */
@SuppressWarnings("unchecked")
public class PriorityBlockingQueue<E> extends AbstractQueue<E> implements BlockingQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = 5595510919245408276L;

    private static final int DEFAULT_INITIAL_CAPACITY = 11;

    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /**
     * 用数组实现的二插小根堆
     */
    private transient Object[] queue;

    private transient int size;

    private transient Comparator<? super E> comparator;

    /**
     * 1个锁 + 一个条件，没有非满条件
     */
    private final ReentrantLock lock = new ReentrantLock();

    private final Condition notEmpty = lock.newCondition();

    private transient volatile int allocationSpinLock;

    private PriorityQueue<E> q;

    public PriorityBlockingQueue() {
        this(DEFAULT_INITIAL_CAPACITY, null);
    }

    public PriorityBlockingQueue(int initialCapacity) {
        this(initialCapacity, null);
    }

    public PriorityBlockingQueue(int initialCapacity, Comparator<? super E> comparator) {
        if (initialCapacity < 1)
            throw new IllegalArgumentException();
        this.comparator = comparator;
        this.queue = new Object[Math.max(1, initialCapacity)];
    }

    public PriorityBlockingQueue(Collection<? extends E> c) {
        boolean heapify = true; // true if not known to be in heap order
        boolean screen = true;  // true if must screen for nulls
        if (c instanceof SortedSet<?>) {
            SortedSet<? extends E> ss = (SortedSet<? extends E>)c;
            this.comparator = (Comparator<? super E>)ss.comparator();
            heapify = false;
        } else if (c instanceof PriorityBlockingQueue<?>) {
            PriorityBlockingQueue<? extends E> pq = (PriorityBlockingQueue<? extends E>)c;
            this.comparator = (Comparator<? super E>)pq.comparator();
            screen = false;
            if (pq.getClass() == PriorityBlockingQueue.class) // exact match
                heapify = false;
        }
        Object[] es = c.toArray();
        int n = es.length;
        if (c.getClass() != java.util.ArrayList.class)
            es = Arrays.copyOf(es, n, Object[].class);
        if (screen && (n == 1 || this.comparator != null)) {
            for (Object e : es)
                if (e == null)
                    throw new NullPointerException();
        }
        this.queue = ensureNonEmpty(es);
        this.size = n;
        if (heapify)
            heapify();
    }
    
    private static Object[] ensureNonEmpty(Object[] es) {
        return (es.length > 0) ? es : new Object[1];
    }

    // ---------------------------------------------------------------->

    /** 
     *
     * @date 2022/7/16 15:19 
     * @param e 
     * @return boolean
     */
    public boolean add(E e) {
        return offer(e);
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/15 23:24
     * @param e
     * @return boolean
     */
    public boolean offer(E e) {
        if (e == null)
            throw new NullPointerException();
        final ReentrantLock lock = this.lock;
        lock.lock();
        int n, cap;
        Object[] es;
        // 超过数组长度，则扩容
        while ((n = size) >= (cap = (es = queue).length))
            tryGrow(es, cap);
        try {
            final Comparator<? super E> cmp;
            // 若没有自定义比较器，则使用自带的比较功能
            if ((cmp = comparator) == null) {
                siftUpComparable(n, e, es);
            } else { // 元素入堆，即执行siftUp操作
                siftUpUsingComparator(n, e, es, cmp);
            }

            size = n + 1;
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
        return true;
    }

    /** 
     *
     * @date 2022/7/16 15:19 
     * @param e
     * @param timeout
     * @param unit 
     * @return boolean
     */
    public boolean offer(E e, long timeout, TimeUnit unit) {
        return offer(e); // never need to block
    }

    /** 
     *
     * @date 2022/7/16 15:19 
     * @param e 
     * @return void
     */
    public void put(E e) {
        offer(e); // never need to block
    }

    /** 
     *
     * @date 2022/7/16 15:20 
     * @param o 
     * @return boolean
     */
    public boolean remove(Object o) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int i = indexOf(o);
            if (i == -1)
                return false;
            removeAt(i);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter);
        return bulkRemove(filter);
    }

    public boolean removeAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return bulkRemove(e -> c.contains(e));
    }

    /** 
     *
     * @date 2022/7/16 15:20 
     * @param  
     * @return E
     */
    public E take() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        E result;
        try {
            // 出队列
            while ((result = dequeue()) == null)
                notEmpty.await();
        } finally {
            lock.unlock();
        }
        return result;
    }

    /** 
     *
     * @date 2022/7/16 15:20 
     * @param  
     * @return E
     */
    public E poll() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return dequeue();
        } finally {
            lock.unlock();
        }
    }

    /** 
     *
     * @date 2022/7/16 15:20 
     * @param timeout
     * @param unit 
     * @return E
     */
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        E result;
        try {
            while ((result = dequeue()) == null && nanos > 0)
                nanos = notEmpty.awaitNanos(nanos);
        } finally {
            lock.unlock();
        }
        return result;
    }

    /** 
     *
     * @date 2022/7/16 15:20
     * @param  
     * @return E
     */
    public E peek() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return (E)queue[0];
        } finally {
            lock.unlock();
        }
    }

    /**
     *
     * @date 2022/7/16 15:15
     * @param array
     * @param oldCap
     * @return void
     */
    private void tryGrow(Object[] array, int oldCap) {
        lock.unlock(); // must release and then re-acquire main lock
        Object[] newArray = null;
        if (allocationSpinLock == 0 && ALLOCATIONSPINLOCK.compareAndSet(this, 0, 1)) {
            try {
                int newCap = oldCap + ((oldCap < 64) ? (oldCap + 2) : // grow faster if small
                                       (oldCap >> 1));
                if (newCap - MAX_ARRAY_SIZE > 0) {    // possible overflow
                    int minCap = oldCap + 1;
                    if (minCap < 0 || minCap > MAX_ARRAY_SIZE)
                        throw new OutOfMemoryError();
                    newCap = MAX_ARRAY_SIZE;
                }
                if (newCap > oldCap && queue == array)
                    newArray = new Object[newCap];
            } finally {
                allocationSpinLock = 0;
            }
        }
        if (newArray == null) // back off if another thread is allocating
            Thread.yield();
        lock.lock();
        if (newArray != null && queue == array) {
            queue = newArray;
            System.arraycopy(array, 0, newArray, 0, oldCap);
        }
    }

    /**
     * 出队列
     * @author liuzhen
     * @date 2022/4/15 23:24
     * @param
     * @return E
     */
    private E dequeue() {
        final Object[] es;
        final E result;

        // 因为是最小二叉堆，堆顶就是要出队的元素
        if ((result = (E)((es = queue)[0])) != null) {
            final int n;
            final E x = (E)es[(n = --size)];
            es[n] = null;
            if (n > 0) {
                final Comparator<? super E> cmp;
                if ((cmp = comparator) == null)
                    // 调整堆，执行siftDown操作
                    siftDownComparable(0, x, es, n);
                else
                    siftDownUsingComparator(0, x, es, n, cmp);
            }
        }
        return result;
    }

    /**
     * 元素下沉 heapify
     * @date 2022/7/16 15:58
     * @param
     * @return void
     */
    private void heapify() {
        final Object[] es = queue;
        int n = size, i = (n >>> 1) - 1;
        final Comparator<? super E> cmp;
        if ((cmp = comparator) == null) {
            for (; i >= 0; i--) {
                siftDownComparable(i, (E)es[i], es, n);
            }
        } else {
            for (; i >= 0; i--) {
                siftDownUsingComparator(i, (E)es[i], es, n, cmp);
            }
        }
    }

    /**
     * 调整元素，使用默认比较器
     * @date 2022/7/16 15:57
     * @param k
     * @param x
     * @param es
     * @param n
     * @return void
     */
    private static <T> void siftDownComparable(int k, T x, Object[] es, int n) {
        Comparable<? super T> key = (Comparable<? super T>)x;
        int half = n >>> 1;           // loop while a non-leaf
        while (k < half) {
            int child = (k << 1) + 1; // assume left child is least
            Object c = es[child];
            int right = child + 1;
            if (right < n && ((Comparable<? super T>)c).compareTo((T)es[right]) > 0) {
                c = es[child = right];
            }
            if (key.compareTo((T)c) <= 0) {
                break;
            }

            es[k] = c;
            k = child;
        }
        es[k] = key;
    }

    /**
     * 调整元素，使用配置比较器
     * @date 2022/7/16 15:58
     * @param k
     * @param x
     * @param es
     * @param n
     * @param cmp
     * @return void
     */
    private static <T> void siftDownUsingComparator(int k, T x, Object[] es, int n, Comparator<? super T> cmp) {
        // assert n > 0;
        int half = n >>> 1;
        while (k < half) {
            int child = (k << 1) + 1;
            Object c = es[child];
            int right = child + 1;
            if (right < n && cmp.compare((T)c, (T)es[right]) > 0) {
                c = es[child = right];
            }
            if (cmp.compare(x, (T)c) <= 0) {
                break;
            }

            es[k] = c;
            k = child;
        }
        es[k] = x;
    }

    // ---------------------------------------------------------------->

    public int size() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return size;
        } finally {
            lock.unlock();
        }
    }

    public Comparator<? super E> comparator() {
        return comparator;
    }

    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    private int indexOf(Object o) {
        if (o != null) {
            final Object[] es = queue;
            for (int i = 0, n = size; i < n; i++)
                if (o.equals(es[i]))
                    return i;
        }
        return -1;
    }

    private void removeAt(int i) {
        final Object[] es = queue;
        final int n = size - 1;
        if (n == i) // removed last element
            es[i] = null;
        else {
            E moved = (E)es[n];
            es[n] = null;
            final Comparator<? super E> cmp;
            if ((cmp = comparator) == null)
                siftDownComparable(i, moved, es, n);
            else
                siftDownUsingComparator(i, moved, es, n, cmp);
            if (es[i] == moved) {
                if (cmp == null)
                    siftUpComparable(i, moved, es);
                else
                    siftUpUsingComparator(i, moved, es, cmp);
            }
        }
        size = n;
    }

    void removeEq(Object o) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final Object[] es = queue;
            for (int i = 0, n = size; i < n; i++) {
                if (o == es[i]) {
                    removeAt(i);
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean contains(Object o) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return indexOf(o) != -1;
        } finally {
            lock.unlock();
        }
    }

    public String toString() {
        return Helpers.collectionToString(this);
    }

    public int drainTo(Collection<? super E> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    public int drainTo(Collection<? super E> c, int maxElements) {
        Objects.requireNonNull(c);
        if (c == this)
            throw new IllegalArgumentException();
        if (maxElements <= 0)
            return 0;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int n = Math.min(size, maxElements);
            for (int i = 0; i < n; i++) {
                c.add((E)queue[0]); // In this order, in case add() throws.
                dequeue();
            }
            return n;
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final Object[] es = queue;
            for (int i = 0, n = size; i < n; i++)
                es[i] = null;
            size = 0;
        } finally {
            lock.unlock();
        }
    }

    public Object[] toArray() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return Arrays.copyOf(queue, size);
        } finally {
            lock.unlock();
        }
    }

    public <T> T[] toArray(T[] a) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int n = size;
            if (a.length < n)
                // Make a new array of a's runtime type, but my contents:
                return (T[])Arrays.copyOf(queue, size, a.getClass());
            System.arraycopy(queue, 0, a, 0, n);
            if (a.length > n)
                a[n] = null;
            return a;
        } finally {
            lock.unlock();
        }
    }

    public Iterator<E> iterator() {
        return new Itr(toArray());
    }

    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        lock.lock();
        try {
            // avoid zero capacity argument
            q = new PriorityQueue<E>(Math.max(size, 1), comparator);
            q.addAll(this);
            s.defaultWriteObject();
        } finally {
            q = null;
            lock.unlock();
        }
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        try {
            s.defaultReadObject();
            int sz = q.size();
            SharedSecrets.getJavaObjectInputStreamAccess().checkArray(s, Object[].class, sz);
            this.queue = new Object[Math.max(1, sz)];
            comparator = q.comparator();
            addAll(q);
        } finally {
            q = null;
        }
    }

    public Spliterator<E> spliterator() {
        return new PBQSpliterator();
    }

    public boolean retainAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return bulkRemove(e -> !c.contains(e));
    }

    // A tiny bit set implementation

    private static long[] nBits(int n) {
        return new long[((n - 1) >> 6) + 1];
    }

    private static void setBit(long[] bits, int i) {
        bits[i >> 6] |= 1L << i;
    }

    private static boolean isClear(long[] bits, int i) {
        return (bits[i >> 6] & (1L << i)) == 0;
    }

    private boolean bulkRemove(Predicate<? super E> filter) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final Object[] es = queue;
            final int end = size;
            int i;
            // Optimize for initial run of survivors
            for (i = 0; i < end && !filter.test((E)es[i]); i++)
                ;
            if (i >= end)
                return false;
            // Tolerate predicates that reentrantly access the
            // collection for read, so traverse once to find elements
            // to delete, a second pass to physically expunge.
            final int beg = i;
            final long[] deathRow = nBits(end - beg);
            deathRow[0] = 1L;   // set bit 0
            for (i = beg + 1; i < end; i++)
                if (filter.test((E)es[i]))
                    setBit(deathRow, i - beg);
            int w = beg;
            for (i = beg; i < end; i++)
                if (isClear(deathRow, i - beg))
                    es[w++] = es[i];
            for (i = size = w; i < end; i++)
                es[i] = null;
            heapify();
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final Object[] es = queue;
            for (int i = 0, n = size; i < n; i++)
                action.accept((E)es[i]);
        } finally {
            lock.unlock();
        }
    }

    private static <T> void siftUpComparable(int k, T x, Object[] es) {
        Comparable<? super T> key = (Comparable<? super T>)x;
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            Object e = es[parent];
            if (key.compareTo((T)e) >= 0)
                break;
            es[k] = e;
            k = parent;
        }
        es[k] = key;
    }

    private static <T> void siftUpUsingComparator(int k, T x, Object[] es, Comparator<? super T> cmp) {
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            Object e = es[parent];
            if (cmp.compare(x, (T)e) >= 0)
                break;
            es[k] = e;
            k = parent;
        }
        es[k] = x;
    }

    // VarHandle mechanics
    private static final VarHandle ALLOCATIONSPINLOCK;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            ALLOCATIONSPINLOCK = l.findVarHandle(PriorityBlockingQueue.class, "allocationSpinLock", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     *
     */
    final class Itr implements Iterator<E> {
        final Object[] array; // Array of all elements
        int cursor;           // index of next element to return
        int lastRet = -1;     // index of last element, or -1 if no such

        Itr(Object[] array) {
            this.array = array;
        }

        public boolean hasNext() {
            return cursor < array.length;
        }

        public E next() {
            if (cursor >= array.length)
                throw new NoSuchElementException();
            return (E)array[lastRet = cursor++];
        }

        public void remove() {
            if (lastRet < 0)
                throw new IllegalStateException();
            removeEq(array[lastRet]);
            lastRet = -1;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            final Object[] es = array;
            int i;
            if ((i = cursor) < es.length) {
                lastRet = -1;
                cursor = es.length;
                for (; i < es.length; i++)
                    action.accept((E)es[i]);
                lastRet = es.length - 1;
            }
        }
    }

    /**
     *
     */
    final class PBQSpliterator implements Spliterator<E> {
        Object[] array;        // null until late-bound-initialized
        int index;
        int fence;

        PBQSpliterator() {
        }

        PBQSpliterator(Object[] array, int index, int fence) {
            this.array = array;
            this.index = index;
            this.fence = fence;
        }

        private int getFence() {
            if (array == null)
                fence = (array = toArray()).length;
            return fence;
        }

        public PBQSpliterator trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid) ? null : new PBQSpliterator(array, lo, index = mid);
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            final int hi = getFence(), lo = index;
            final Object[] es = array;
            index = hi;                 // ensure exhaustion
            for (int i = lo; i < hi; i++)
                action.accept((E)es[i]);
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            if (getFence() > index && index >= 0) {
                action.accept((E)array[index++]);
                return true;
            }
            return false;
        }

        public long estimateSize() {
            return getFence() - index;
        }

        public int characteristics() {
            return (Spliterator.NONNULL | Spliterator.SIZED | Spliterator.SUBSIZED);
        }
    }
}
