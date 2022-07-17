/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */


package java.util.concurrent;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * LinkedBlockingQueue
 * 是一种基于单向链表的阻塞队列。因为队头和队尾是2个指针分开操作的，所以用了2把锁+2个条件，同时有1个AtomicInteger的原子变量记录count数。
 * LinkedBlockingQueue和ArrayBlockingQueue的差异：
 * 1. 为了提高并发度，用2把锁，分别控制队头、队尾的操作。意味着在put(...)和put(...)之间、
 *  take()与take()之间是互斥的，put(...)和take()之间并不互斥。但对于count变量，双方都需要操作，所以必须是原子类型。
 * 2. 因为各自拿了一把锁，所以当需要调用对方的condition的signal时，还必须再加上对方的锁，就是signalNotEmpty()和signalNotFull()方法。
 * 3. 不仅put会通知 take，take 也会通知 put。当put 发现非满的时候，也会通知其他 put线程；当take发现非空的时候，也会通知其他take线程
 * @date 2022/7/16 14:28
 */
public class LinkedBlockingQueue<E> extends AbstractQueue<E> implements BlockingQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = -6903933977591709194L;

    private final int capacity;

    /** 原子变量 */
    private final AtomicInteger count = new AtomicInteger();

    /** 单向链表的头部 */
    transient Node<E> head;
    /** 单向链表的尾部 */
    private transient Node<E> last;

    /** 两把锁，两个条件 */
    private final ReentrantLock putLock = new ReentrantLock();
    private final Condition notFull = putLock.newCondition();

    private final ReentrantLock takeLock = new ReentrantLock();
    private final Condition notEmpty = takeLock.newCondition();


    /**
     * Node
     * @date 2022/7/16 14:22
     */
    static class Node<E> {
        E item;

        Node<E> next;

        Node(E x) {
            item = x;
        }
    }

    public LinkedBlockingQueue() {
        this(Integer.MAX_VALUE);
    }

    /**
     * 构造方法
     * @date 2022/7/16 15:00
     * @param capacity 指定队列的总容量
     * @return
     */
    public LinkedBlockingQueue(int capacity) {
        if (capacity <= 0)
            throw new IllegalArgumentException();
        this.capacity = capacity;
        last = head = new Node<E>(null);
    }

    public LinkedBlockingQueue(Collection<? extends E> c) {
        this(Integer.MAX_VALUE);

        final ReentrantLock putLock = this.putLock;
        putLock.lock(); // Never contended, but necessary for visibility
        try {
            int n = 0;
            for (E e : c) {
                if (e == null)
                    throw new NullPointerException();
                if (n == capacity)
                    throw new IllegalStateException("Queue full");
                enqueue(new Node<E>(e));
                ++n;
            }
            count.set(n);
        } finally {
            putLock.unlock();
        }
    }

    // ---------------------------------------------------------------->

    /**
     * 添加元素
     * @date 2022/7/16 14:25
     * @param e
     * @return boolean
     */
    public boolean offer(E e) {
        if (e == null)
            throw new NullPointerException();
        final AtomicInteger count = this.count;
        if (count.get() == capacity)
            return false;
        final int c;
        final Node<E> node = new Node<E>(e);
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {
            if (count.get() == capacity) {
                return false;
            }

            enqueue(node);
            c = count.getAndIncrement();
            if (c + 1 < capacity) {
                notFull.signal();
            }
        } finally {
            putLock.unlock();
        }

        if (c == 0) {
            signalNotEmpty();
        }
        return true;
    }

    /**
     *
     * @date 2022/7/16 14:25
     * @param e
     * @param timeout
     * @param unit
     * @return boolean
     */
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        if (e == null)
            throw new NullPointerException();

        long nanos = unit.toNanos(timeout);
        final int c;
        final ReentrantLock putLock = this.putLock;
        final AtomicInteger count = this.count;

        putLock.lockInterruptibly();
        try {
            while (count.get() == capacity) {
                if (nanos <= 0L) {
                    return false;
                }
                nanos = notFull.awaitNanos(nanos);
            }

            enqueue(new Node<E>(e));
            c = count.getAndIncrement();
            if (c + 1 < capacity) {
                notFull.signal();
            }
        } finally {
            putLock.unlock();
        }

        if (c == 0) {
            signalNotEmpty();
        }

        return true;
    }

    /**
     *
     * @date 2022/7/16 14:25
     * @param e
     * @return void
     */
    public void put(E e) throws InterruptedException {
        if (e == null)
            throw new NullPointerException();
        final int c;
        final Node<E> node = new Node<E>(e);
        final ReentrantLock putLock = this.putLock;
        final AtomicInteger count = this.count;
        putLock.lockInterruptibly();
        try {
            while (count.get() == capacity) {
                notFull.await();
            }

            enqueue(node);
            c = count.getAndIncrement();
            // 如果队列中还剩余空间，则通知其他 put() 线程
            if (c + 1 < capacity) {
                notFull.signal();
            }
        } finally {
            putLock.unlock();
        }

        if (c == 0) {
            signalNotEmpty();
        }
    }

    /**
     *
     * @date 2022/7/16 14:26
     * @param o
     * @return boolean
     */
    public boolean remove(Object o) {
        if (o == null)
            return false;
        fullyLock();
        try {
            for (Node<E> pred = head, p = pred.next; p != null; pred = p, p = p.next) {
                if (o.equals(p.item)) {
                    unlink(p, pred);
                    return true;
                }
            }
            return false;
        } finally {
            fullyUnlock();
        }
    }

    /**
     *
     * @date 2022/7/16 14:25
     * @param
     * @return E
     */
    public E take() throws InterruptedException {
        final E x;
        final int c;
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lockInterruptibly();
        try {
            while (count.get() == 0) {
                notEmpty.await();
            }
            x = dequeue();
            c = count.getAndDecrement();
            // 如果还有元素，则通知其他 take() 线程
            if (c > 1) {
                notEmpty.signal();
            }
        } finally {
            takeLock.unlock();
        }
        if (c == capacity)
            signalNotFull();
        return x;
    }

    /**
     *
     * @date 2022/7/16 14:26
     * @param
     * @return E
     */
    public E poll() {
        final AtomicInteger count = this.count;
        if (count.get() == 0)
            return null;
        final E x;
        final int c;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            if (count.get() == 0)
                return null;
            x = dequeue();
            c = count.getAndDecrement();
            if (c > 1)
                notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
        if (c == capacity)
            signalNotFull();
        return x;
    }

    /**
     *
     * @date 2022/7/16 14:26
     * @param timeout
     * @param unit
     * @return E
     */
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        final E x;
        final int c;
        long nanos = unit.toNanos(timeout);
        final AtomicInteger count = this.count;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lockInterruptibly();
        try {
            while (count.get() == 0) {
                if (nanos <= 0L)
                    return null;
                nanos = notEmpty.awaitNanos(nanos);
            }
            x = dequeue();
            c = count.getAndDecrement();
            if (c > 1)
                notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
        if (c == capacity)
            signalNotFull();
        return x;
    }

    /**
     *
     * @date 2022/7/16 14:26
     * @param
     * @return E
     */
    public E peek() {
        final AtomicInteger count = this.count;
        if (count.get() == 0)
            return null;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            return (count.get() > 0) ? head.next.item : null;
        } finally {
            takeLock.unlock();
        }
    }

    /**
     * 进队列
     * @date 2022/7/16 14:23
     * @param node
     * @return void
     */
    private void enqueue(Node<E> node) {
        // assert putLock.isHeldByCurrentThread();
        // assert last.next == null;
        last = last.next = node;
    }

    /**
     * 出队列
     * @date 2022/7/16 14:23
     * @param
     * @return E
     */
    private E dequeue() {
        Node<E> h = head;
        Node<E> first = h.next;
        h.next = h; // help GC
        head = first;
        E x = first.item;
        first.item = null;
        return x;
    }

    /**
     * notEmpty唤醒
     * @date 2022/7/16 14:23
     * @param
     * @return void
     */
    private void signalNotEmpty() {
        final ReentrantLock takeLock = this.takeLock;

        // 必须先获取 takeLock 菜可以调用 notEmpty.signal() 方法
        takeLock.lock();
        try {
            notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
    }

    /**
     * notFull唤醒
     * @date 2022/7/16 14:24
     * @param
     * @return void
     */
    private void signalNotFull() {
        final ReentrantLock putLock = this.putLock;

        // 必须先获取 putLock 菜可以调用 notFull.signal() 方法
        putLock.lock();
        try {
            notFull.signal();
        } finally {
            putLock.unlock();
        }
    }

    void fullyLock() {
        putLock.lock();
        takeLock.lock();
    }

    void fullyUnlock() {
        takeLock.unlock();
        putLock.unlock();
    }

    // ---------------------------------------------------------------->

    public int size() {
        return count.get();
    }

    public int remainingCapacity() {
        return capacity - count.get();
    }

    void unlink(Node<E> p, Node<E> pred) {
        // assert putLock.isHeldByCurrentThread();
        // assert takeLock.isHeldByCurrentThread();
        // p.next is not changed, to allow iterators that are
        // traversing p to maintain their weak-consistency guarantee.
        p.item = null;
        pred.next = p.next;
        if (last == p)
            last = pred;
        if (count.getAndDecrement() == capacity)
            notFull.signal();
    }

    public boolean contains(Object o) {
        if (o == null)
            return false;
        fullyLock();
        try {
            for (Node<E> p = head.next; p != null; p = p.next)
                if (o.equals(p.item))
                    return true;
            return false;
        } finally {
            fullyUnlock();
        }
    }

    public Object[] toArray() {
        fullyLock();
        try {
            int size = count.get();
            Object[] a = new Object[size];
            int k = 0;
            for (Node<E> p = head.next; p != null; p = p.next)
                a[k++] = p.item;
            return a;
        } finally {
            fullyUnlock();
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        fullyLock();
        try {
            int size = count.get();
            if (a.length < size)
                a = (T[])java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);

            int k = 0;
            for (Node<E> p = head.next; p != null; p = p.next)
                a[k++] = (T)p.item;
            if (a.length > k)
                a[k] = null;
            return a;
        } finally {
            fullyUnlock();
        }
    }

    public String toString() {
        return Helpers.collectionToString(this);
    }

    public void clear() {
        fullyLock();
        try {
            for (Node<E> p, h = head; (p = h.next) != null; h = p) {
                h.next = h;
                p.item = null;
            }
            head = last;
            // assert head.item == null && head.next == null;
            if (count.getAndSet(0) == capacity)
                notFull.signal();
        } finally {
            fullyUnlock();
        }
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
        boolean signalNotFull = false;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            int n = Math.min(maxElements, count.get());
            // count.get provides visibility to first n Nodes
            Node<E> h = head;
            int i = 0;
            try {
                while (i < n) {
                    Node<E> p = h.next;
                    c.add(p.item);
                    p.item = null;
                    h.next = h;
                    h = p;
                    ++i;
                }
                return n;
            } finally {
                // Restore invariants even if c.add() threw
                if (i > 0) {
                    // assert h.item == null;
                    head = h;
                    signalNotFull = (count.getAndAdd(-i) == capacity);
                }
            }
        } finally {
            takeLock.unlock();
            if (signalNotFull)
                signalNotFull();
        }
    }

    Node<E> succ(Node<E> p) {
        if (p == (p = p.next))
            p = head.next;
        return p;
    }

    public Iterator<E> iterator() {
        return new Itr();
    }

    public Spliterator<E> spliterator() {
        return new LBQSpliterator();
    }

    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        forEachFrom(action, null);
    }

    void forEachFrom(Consumer<? super E> action, Node<E> p) {
        final int batchSize = 64;       // max number of elements per batch
        Object[] es = null;             // container for batch of elements
        int n, len = 0;
        do {
            fullyLock();
            try {
                if (es == null) {
                    if (p == null)
                        p = head.next;
                    for (Node<E> q = p; q != null; q = succ(q))
                        if (q.item != null && ++len == batchSize)
                            break;
                    es = new Object[len];
                }
                for (n = 0; p != null && n < len; p = succ(p))
                    if ((es[n] = p.item) != null)
                        n++;
            } finally {
                fullyUnlock();
            }
            for (int i = 0; i < n; i++) {
                @SuppressWarnings("unchecked") E e = (E)es[i];
                action.accept(e);
            }
        } while (n > 0 && p != null);
    }

    public boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter);
        return bulkRemove(filter);
    }

    public boolean removeAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return bulkRemove(e -> c.contains(e));
    }

    public boolean retainAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return bulkRemove(e -> !c.contains(e));
    }

    Node<E> findPred(Node<E> p, Node<E> ancestor) {
        // assert p.item != null;
        if (ancestor.item == null)
            ancestor = head;
        // Fails with NPE if precondition not satisfied
        for (Node<E> q; (q = ancestor.next) != p; )
            ancestor = q;
        return ancestor;
    }

    @SuppressWarnings("unchecked")
    private boolean bulkRemove(Predicate<? super E> filter) {
        boolean removed = false;
        Node<E> p = null, ancestor = head;
        Node<E>[] nodes = null;
        int n, len = 0;
        do {
            // 1. Extract batch of up to 64 elements while holding the lock.
            fullyLock();
            try {
                if (nodes == null) {  // first batch; initialize
                    p = head.next;
                    for (Node<E> q = p; q != null; q = succ(q))
                        if (q.item != null && ++len == 64)
                            break;
                    nodes = (Node<E>[])new Node<?>[len];
                }
                for (n = 0; p != null && n < len; p = succ(p))
                    nodes[n++] = p;
            } finally {
                fullyUnlock();
            }

            // 2. Run the filter on the elements while lock is free.
            long deathRow = 0L;       // "bitset" of size 64
            for (int i = 0; i < n; i++) {
                final E e;
                if ((e = nodes[i].item) != null && filter.test(e))
                    deathRow |= 1L << i;
            }

            // 3. Remove any filtered elements while holding the lock.
            if (deathRow != 0) {
                fullyLock();
                try {
                    for (int i = 0; i < n; i++) {
                        final Node<E> q;
                        if ((deathRow & (1L << i)) != 0L && (q = nodes[i]).item != null) {
                            ancestor = findPred(q, ancestor);
                            unlink(q, ancestor);
                            removed = true;
                        }
                        nodes[i] = null; // help GC
                    }
                } finally {
                    fullyUnlock();
                }
            }
        } while (n > 0 && p != null);
        return removed;
    }

    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {

        fullyLock();
        try {
            // Write out any hidden stuff, plus capacity
            s.defaultWriteObject();

            // Write out all elements in the proper order.
            for (Node<E> p = head.next; p != null; p = p.next)
                s.writeObject(p.item);

            // Use trailing null as sentinel
            s.writeObject(null);
        } finally {
            fullyUnlock();
        }
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        // Read in capacity, and any hidden stuff
        s.defaultReadObject();

        count.set(0);
        last = head = new Node<E>(null);

        // Read in all elements and place in queue
        for (; ; ) {
            @SuppressWarnings("unchecked") E item = (E)s.readObject();
            if (item == null)
                break;
            add(item);
        }
    }

    /**
     *
     */
    private class Itr implements Iterator<E> {
        private Node<E> next;           // Node holding nextItem
        private E nextItem;             // next item to hand out
        private Node<E> lastRet;
        private Node<E> ancestor;       // Helps unlink lastRet on remove()

        Itr() {
            fullyLock();
            try {
                if ((next = head.next) != null)
                    nextItem = next.item;
            } finally {
                fullyUnlock();
            }
        }

        public boolean hasNext() {
            return next != null;
        }

        public E next() {
            Node<E> p;
            if ((p = next) == null)
                throw new NoSuchElementException();
            lastRet = p;
            E x = nextItem;
            fullyLock();
            try {
                E e = null;
                for (p = p.next; p != null && (e = p.item) == null; )
                    p = succ(p);
                next = p;
                nextItem = e;
            } finally {
                fullyUnlock();
            }
            return x;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            // A variant of forEachFrom
            Objects.requireNonNull(action);
            Node<E> p;
            if ((p = next) == null)
                return;
            lastRet = p;
            next = null;
            final int batchSize = 64;
            Object[] es = null;
            int n, len = 1;
            do {
                fullyLock();
                try {
                    if (es == null) {
                        p = p.next;
                        for (Node<E> q = p; q != null; q = succ(q))
                            if (q.item != null && ++len == batchSize)
                                break;
                        es = new Object[len];
                        es[0] = nextItem;
                        nextItem = null;
                        n = 1;
                    } else
                        n = 0;
                    for (; p != null && n < len; p = succ(p))
                        if ((es[n] = p.item) != null) {
                            lastRet = p;
                            n++;
                        }
                } finally {
                    fullyUnlock();
                }
                for (int i = 0; i < n; i++) {
                    @SuppressWarnings("unchecked") E e = (E)es[i];
                    action.accept(e);
                }
            } while (n > 0 && p != null);
        }

        public void remove() {
            Node<E> p = lastRet;
            if (p == null)
                throw new IllegalStateException();
            lastRet = null;
            fullyLock();
            try {
                if (p.item != null) {
                    if (ancestor == null)
                        ancestor = head;
                    ancestor = findPred(p, ancestor);
                    unlink(p, ancestor);
                }
            } finally {
                fullyUnlock();
            }
        }
    }

    /**
     *
     */
    private final class LBQSpliterator implements Spliterator<E> {
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        Node<E> current;    // current node; null until initialized
        int batch;          // batch size for splits
        boolean exhausted;  // true when no more nodes
        long est = size();  // size estimate

        LBQSpliterator() {
        }

        public long estimateSize() {
            return est;
        }

        public Spliterator<E> trySplit() {
            Node<E> h;
            if (!exhausted && ((h = current) != null || (h = head.next) != null) && h.next != null) {
                int n = batch = Math.min(batch + 1, MAX_BATCH);
                Object[] a = new Object[n];
                int i = 0;
                Node<E> p = current;
                fullyLock();
                try {
                    if (p != null || (p = head.next) != null)
                        for (; p != null && i < n; p = succ(p))
                            if ((a[i] = p.item) != null)
                                i++;
                } finally {
                    fullyUnlock();
                }
                if ((current = p) == null) {
                    est = 0L;
                    exhausted = true;
                } else if ((est -= i) < 0L)
                    est = 0L;
                if (i > 0)
                    return Spliterators.spliterator(a, 0, i, (Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.CONCURRENT));
            }
            return null;
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            if (!exhausted) {
                E e = null;
                fullyLock();
                try {
                    Node<E> p;
                    if ((p = current) != null || (p = head.next) != null)
                        do {
                            e = p.item;
                            p = succ(p);
                        } while (e == null && p != null);
                    if ((current = p) == null)
                        exhausted = true;
                } finally {
                    fullyUnlock();
                }
                if (e != null) {
                    action.accept(e);
                    return true;
                }
            }
            return false;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            if (!exhausted) {
                exhausted = true;
                Node<E> p = current;
                current = null;
                forEachFrom(action, p);
            }
        }

        public int characteristics() {
            return (Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.CONCURRENT);
        }
    }
}
