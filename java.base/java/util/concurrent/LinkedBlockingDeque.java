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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * LinkedBlockingDeque
 * 对应的实现原理，和LinkedBlockingQueue基本一样，只是LinkedBlockingQueue是单向链表，而LinkedBlockingDeque是双向链表。
 * @author liuzhen
 * @date 2022/4/15 23:51
 * @return
 */
public class LinkedBlockingDeque<E> extends AbstractQueue<E> implements BlockingDeque<E>, java.io.Serializable {

    private static final long serialVersionUID = -387911632671998426L;

    /** 队列的头和尾 */
    transient Node<E> first;

    transient Node<E> last;

    /** 元素个数 */
    private transient int count;

    /** 容量 */
    private final int capacity;

    /** 一把锁+两个条件 */
    final ReentrantLock lock = new ReentrantLock();

    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();

    /**
     *
     * @date 2022/7/16 17:23
     */
    static final class Node<E> {
        E item;
        /** 双向链表的Node */
        Node<E> prev;

        Node<E> next;

        Node(E x) {
            item = x;
        }
    }

    public LinkedBlockingDeque() {
        this(Integer.MAX_VALUE);
    }

    public LinkedBlockingDeque(int capacity) {
        if (capacity <= 0)
            throw new IllegalArgumentException();
        this.capacity = capacity;
    }

    public LinkedBlockingDeque(Collection<? extends E> c) {
        this(Integer.MAX_VALUE);
        addAll(c);
    }


    /** 1. BlockingDeque start =========================================================> */
    // 添加元素 ---------------------------------------------------------------->
    // first ----------------------------->
    public void push(E e) {
        addFirst(e);
    }

    public void addFirst(E e) {
        if (!offerFirst(e))
            throw new IllegalStateException("Deque full");
    }

    public boolean offerFirst(E e) {
        if (e == null)
            throw new NullPointerException();
        Node<E> node = new Node<E>(e);
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return linkFirst(node);
        } finally {
            lock.unlock();
        }
    }

    public boolean offerFirst(E e, long timeout, TimeUnit unit) throws InterruptedException {
        if (e == null)
            throw new NullPointerException();
        Node<E> node = new Node<E>(e);
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (!linkFirst(node)) {
                if (nanos <= 0L)
                    return false;
                nanos = notFull.awaitNanos(nanos);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** 
     *
     * @date 2022/7/16 17:43
     * @param e 
     * @return void
     */
    public void putFirst(E e) throws InterruptedException {
        if (e == null)
            throw new NullPointerException();
        Node<E> node = new Node<E>(e);
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            while (!linkFirst(node))
                notFull.await();
        } finally {
            lock.unlock();
        }
    }

    // last ----------------------------->
    public boolean add(E e) {
        addLast(e);
        return true;
    }

    public void addLast(E e) {
        if (!offerLast(e))
            throw new IllegalStateException("Deque full");
    }

    public boolean offer(E e) {
        return offerLast(e);
    }

    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        return offerLast(e, timeout, unit);
    }

    public boolean offerLast(E e) {
        if (e == null)
            throw new NullPointerException();
        Node<E> node = new Node<E>(e);
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return linkLast(node);
        } finally {
            lock.unlock();
        }
    }

    public boolean offerLast(E e, long timeout, TimeUnit unit) throws InterruptedException {
        if (e == null)
            throw new NullPointerException();
        Node<E> node = new Node<E>(e);
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (!linkLast(node)) {
                if (nanos <= 0L)
                    return false;
                nanos = notFull.awaitNanos(nanos);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void put(E e) throws InterruptedException {
        putLast(e);
    }

    /**
     *
     * @date 2022/7/16 17:43
     * @param e
     * @return void
     */
    public void putLast(E e) throws InterruptedException {
        if (e == null)
            throw new NullPointerException();
        Node<E> node = new Node<E>(e);
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            while (!linkLast(node))
                notFull.await();
        } finally {
            lock.unlock();
        }
    }

    // 删除元素 ---------------------------------------------------------------->
    // first ----------------------------->
    public E remove() {
        return removeFirst();
    }

    public boolean remove(Object o) {
        return removeFirstOccurrence(o);
    }

    public boolean removeFirstOccurrence(Object o) {
        if (o == null)
            return false;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            for (Node<E> p = first; p != null; p = p.next) {
                if (o.equals(p.item)) {
                    unlink(p);
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public E take() throws InterruptedException {
        return takeFirst();
    }

    /**
     * 对应的实现原理，和LinkedBlockingQueue基本一样，只是LinkedBlockingQueue是单向链表，而LinkedBlockingDeque是双向链表。
     * @author liuzhen
     * @date 2022/4/15 23:53
     * @param
     * @return E
     */
    public E takeFirst() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            E x;
            while ((x = unlinkFirst()) == null)
                notEmpty.await();
            return x;
        } finally {
            lock.unlock();
        }
    }

    public E poll() {
        return pollFirst();
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        return pollFirst(timeout, unit);
    }

    public E pollFirst(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            E x;
            while ((x = unlinkFirst()) == null) {
                if (nanos <= 0L)
                    return null;
                nanos = notEmpty.awaitNanos(nanos);
            }
            return x;
        } finally {
            lock.unlock();
        }
    }

    // last ----------------------------->
    /** 
     *
     * @date 2022/7/16 17:42 
     * @param  
     * @return E
     */
    public E takeLast() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            E x;
            while ((x = unlinkLast()) == null)
                notEmpty.await();
            return x;
        } finally {
            lock.unlock();
        }
    }

    public E pollLast(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            E x;
            while ((x = unlinkLast()) == null) {
                if (nanos <= 0L)
                    return null;
                nanos = notEmpty.awaitNanos(nanos);
            }
            return x;
        } finally {
            lock.unlock();
        }
    }

    public boolean removeLastOccurrence(Object o) {
        if (o == null)
            return false;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            for (Node<E> p = last; p != null; p = p.prev) {
                if (o.equals(p.item)) {
                    unlink(p);
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    // 获取元素 ---------------------------------------------------------------->
    public E peek() {
        return peekFirst();
    }

    public E element() {
        return getFirst();
    }

    // 其他---------------------------------------------------------------->
    public int size() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    public boolean contains(Object o) {
        if (o == null)
            return false;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            for (Node<E> p = first; p != null; p = p.next)
                if (o.equals(p.item))
                    return true;
            return false;
        } finally {
            lock.unlock();
        }
    }

    public Iterator<E> iterator() {
        return new Itr();
    }

    // private ---------------------------------------------------------------->
    private boolean linkFirst(Node<E> node) {
        // assert lock.isHeldByCurrentThread();
        if (count >= capacity)
            return false;
        Node<E> f = first;
        node.next = f;
        first = node;
        if (last == null)
            last = node;
        else
            f.prev = node;
        ++count;
        notEmpty.signal();
        return true;
    }

    private boolean linkLast(Node<E> node) {
        // assert lock.isHeldByCurrentThread();
        if (count >= capacity)
            return false;
        Node<E> l = last;
        node.prev = l;
        last = node;
        if (first == null)
            first = node;
        else
            l.next = node;
        ++count;
        notEmpty.signal();
        return true;
    }

    void unlink(Node<E> x) {
        // assert lock.isHeldByCurrentThread();
        // assert x.item != null;
        Node<E> p = x.prev;
        Node<E> n = x.next;
        if (p == null) {
            unlinkFirst();
        } else if (n == null) {
            unlinkLast();
        } else {
            p.next = n;
            n.prev = p;
            x.item = null;
            // Don't mess with x's links.  They may still be in use by
            // an iterator.
            --count;
            notFull.signal();
        }
    }

    private E unlinkFirst() {
        // assert lock.isHeldByCurrentThread();
        Node<E> f = first;
        if (f == null)
            return null;
        Node<E> n = f.next;
        E item = f.item;
        f.item = null;
        f.next = f; // help GC
        first = n;
        if (n == null)
            last = null;
        else
            n.prev = null;
        --count;
        notFull.signal();
        return item;
    }

    private E unlinkLast() {
        // assert lock.isHeldByCurrentThread();
        Node<E> l = last;
        if (l == null)
            return null;
        Node<E> p = l.prev;
        E item = l.item;
        l.item = null;
        l.prev = l; // help GC
        last = p;
        if (p == null)
            first = null;
        else
            p.next = null;
        --count;
        notFull.signal();
        return item;
    }

    /** 1. BlockingDeque end =========================================================> */

    /** 2. Deque start =========================================================> */

    public E pop() {
        return removeFirst();
    }

    public E removeFirst() {
        E x = pollFirst();
        if (x == null)
            throw new NoSuchElementException();
        return x;
    }

    public E removeLast() {
        E x = pollLast();
        if (x == null)
            throw new NoSuchElementException();
        return x;
    }

    public E pollFirst() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return unlinkFirst();
        } finally {
            lock.unlock();
        }
    }

    public E pollLast() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return unlinkLast();
        } finally {
            lock.unlock();
        }
    }

    public E getFirst() {
        E x = peekFirst();
        if (x == null)
            throw new NoSuchElementException();
        return x;
    }

    public E getLast() {
        E x = peekLast();
        if (x == null)
            throw new NoSuchElementException();
        return x;
    }

    public E peekFirst() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return (first == null) ? null : first.item;
        } finally {
            lock.unlock();
        }
    }

    public E peekLast() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return (last == null) ? null : last.item;
        } finally {
            lock.unlock();
        }
    }

    public Iterator<E> descendingIterator() {
        return new DescendingItr();
    }

    /** 2. Deque end =========================================================> */

    /** 3. BlockingQueue start =========================================================> */

    public int remainingCapacity() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return capacity - count;
        } finally {
            lock.unlock();
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
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int n = Math.min(maxElements, count);
            for (int i = 0; i < n; i++) {
                c.add(first.item);   // In this order, in case add() throws.
                unlinkFirst();
            }
            return n;
        } finally {
            lock.unlock();
        }
    }

    /** 3. BlockingQueue end =========================================================> */

    // Collection methods

    public boolean addAll(Collection<? extends E> c) {
        if (c == this)
            // As historically specified in AbstractQueue#addAll
            throw new IllegalArgumentException();

        // Copy c into a private chain of Nodes
        Node<E> beg = null, end = null;
        int n = 0;
        for (E e : c) {
            Objects.requireNonNull(e);
            n++;
            Node<E> newNode = new Node<E>(e);
            if (beg == null)
                beg = end = newNode;
            else {
                end.next = newNode;
                newNode.prev = end;
                end = newNode;
            }
        }
        if (beg == null)
            return false;

        // Atomically append the chain at the end
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (count + n <= capacity) {
                beg.prev = last;
                if (first == null)
                    first = beg;
                else
                    last.next = beg;
                last = end;
                count += n;
                notEmpty.signalAll();
                return true;
            }
        } finally {
            lock.unlock();
        }
        // Fall back to historic non-atomic implementation, failing
        // with IllegalStateException when the capacity is exceeded.
        return super.addAll(c);
    }

    public boolean removeAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return bulkRemove(e -> c.contains(e));
    }

    @SuppressWarnings("unchecked")
    public Object[] toArray() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] a = new Object[count];
            int k = 0;
            for (Node<E> p = first; p != null; p = p.next)
                a[k++] = p.item;
            return a;
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (a.length < count)
                a = (T[])java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), count);

            int k = 0;
            for (Node<E> p = first; p != null; p = p.next)
                a[k++] = (T)p.item;
            if (a.length > k)
                a[k] = null;
            return a;
        } finally {
            lock.unlock();
        }
    }

    public String toString() {
        return Helpers.collectionToString(this);
    }

    public void clear() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            for (Node<E> f = first; f != null; ) {
                f.item = null;
                Node<E> n = f.next;
                f.prev = null;
                f.next = null;
                f = n;
            }
            first = last = null;
            count = 0;
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public Spliterator<E> spliterator() {
        return new LBDSpliterator();
    }

    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        forEachFrom(action, null);
    }

    public boolean removeIf(Predicate<? super E> filter) {
        Objects.requireNonNull(filter);
        return bulkRemove(filter);
    }

    public boolean retainAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return bulkRemove(e -> !c.contains(e));
    }

    Node<E> succ(Node<E> p) {
        if (p == (p = p.next))
            p = first;
        return p;
    }

    void forEachFrom(Consumer<? super E> action, Node<E> p) {
        // Extract batches of elements while holding the lock; then
        // run the action on the elements while not
        final ReentrantLock lock = this.lock;
        final int batchSize = 64;       // max number of elements per batch
        Object[] es = null;             // container for batch of elements
        int n, len = 0;
        do {
            lock.lock();
            try {
                if (es == null) {
                    if (p == null)
                        p = first;
                    for (Node<E> q = p; q != null; q = succ(q))
                        if (q.item != null && ++len == batchSize)
                            break;
                    es = new Object[len];
                }
                for (n = 0; p != null && n < len; p = succ(p))
                    if ((es[n] = p.item) != null)
                        n++;
            } finally {
                lock.unlock();
            }
            for (int i = 0; i < n; i++) {
                @SuppressWarnings("unchecked") E e = (E)es[i];
                action.accept(e);
            }
        } while (n > 0 && p != null);
    }

    @SuppressWarnings("unchecked")
    private boolean bulkRemove(Predicate<? super E> filter) {
        boolean removed = false;
        final ReentrantLock lock = this.lock;
        Node<E> p = null;
        Node<E>[] nodes = null;
        int n, len = 0;
        do {
            // 1. Extract batch of up to 64 elements while holding the lock.
            lock.lock();
            try {
                if (nodes == null) {  // first batch; initialize
                    p = first;
                    for (Node<E> q = p; q != null; q = succ(q))
                        if (q.item != null && ++len == 64)
                            break;
                    nodes = (Node<E>[])new Node<?>[len];
                }
                for (n = 0; p != null && n < len; p = succ(p))
                    nodes[n++] = p;
            } finally {
                lock.unlock();
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
                lock.lock();
                try {
                    for (int i = 0; i < n; i++) {
                        final Node<E> q;
                        if ((deathRow & (1L << i)) != 0L && (q = nodes[i]).item != null) {
                            unlink(q);
                            removed = true;
                        }
                        nodes[i] = null; // help GC
                    }
                } finally {
                    lock.unlock();
                }
            }
        } while (n > 0 && p != null);
        return removed;
    }

    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            // Write out capacity and any hidden stuff
            s.defaultWriteObject();
            // Write out all elements in the proper order.
            for (Node<E> p = first; p != null; p = p.next)
                s.writeObject(p.item);
            // Use trailing null as sentinel
            s.writeObject(null);
        } finally {
            lock.unlock();
        }
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        count = 0;
        first = null;
        last = null;
        // Read in all elements and place in queue
        for (; ; ) {
            @SuppressWarnings("unchecked") E item = (E)s.readObject();
            if (item == null)
                break;
            add(item);
        }
    }

    void checkInvariants() {
        // assert lock.isHeldByCurrentThread();
        // Nodes may get self-linked or lose their item, but only
        // after being unlinked and becoming unreachable from first.
        for (Node<E> p = first; p != null; p = p.next) {
            // assert p.next != p;
            // assert p.item != null;
        }
    }


    /**
     *
     */
    private abstract class AbstractItr implements Iterator<E> {
        Node<E> next;

        E nextItem;

        private Node<E> lastRet;

        abstract Node<E> firstNode();

        abstract Node<E> nextNode(Node<E> n);

        private Node<E> succ(Node<E> p) {
            if (p == (p = nextNode(p)))
                p = firstNode();
            return p;
        }

        AbstractItr() {
            // set to initial position
            final ReentrantLock lock = LinkedBlockingDeque.this.lock;
            lock.lock();
            try {
                if ((next = firstNode()) != null)
                    nextItem = next.item;
            } finally {
                lock.unlock();
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
            final ReentrantLock lock = LinkedBlockingDeque.this.lock;
            lock.lock();
            try {
                E e = null;
                for (p = nextNode(p); p != null && (e = p.item) == null; )
                    p = succ(p);
                next = p;
                nextItem = e;
            } finally {
                lock.unlock();
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
            final ReentrantLock lock = LinkedBlockingDeque.this.lock;
            final int batchSize = 64;
            Object[] es = null;
            int n, len = 1;
            do {
                lock.lock();
                try {
                    if (es == null) {
                        p = nextNode(p);
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
                    lock.unlock();
                }
                for (int i = 0; i < n; i++) {
                    @SuppressWarnings("unchecked") E e = (E)es[i];
                    action.accept(e);
                }
            } while (n > 0 && p != null);
        }

        public void remove() {
            Node<E> n = lastRet;
            if (n == null)
                throw new IllegalStateException();
            lastRet = null;
            final ReentrantLock lock = LinkedBlockingDeque.this.lock;
            lock.lock();
            try {
                if (n.item != null)
                    unlink(n);
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     *
     */
    private class Itr extends AbstractItr {
        Itr() {
        }                        // prevent access constructor creation

        Node<E> firstNode() {
            return first;
        }

        Node<E> nextNode(Node<E> n) {
            return n.next;
        }
    }

    /**
     *
     */
    private class DescendingItr extends AbstractItr {
        DescendingItr() {
        }              // prevent access constructor creation

        Node<E> firstNode() {
            return last;
        }

        Node<E> nextNode(Node<E> n) {
            return n.prev;
        }
    }

    /**
     *
     */
    private final class LBDSpliterator implements Spliterator<E> {
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        Node<E> current;    // current node; null until initialized
        int batch;          // batch size for splits
        boolean exhausted;  // true when no more nodes
        long est = size();  // size estimate

        LBDSpliterator() {
        }

        public long estimateSize() {
            return est;
        }

        public Spliterator<E> trySplit() {
            Node<E> h;
            if (!exhausted && ((h = current) != null || (h = first) != null) && h.next != null) {
                int n = batch = Math.min(batch + 1, MAX_BATCH);
                Object[] a = new Object[n];
                final ReentrantLock lock = LinkedBlockingDeque.this.lock;
                int i = 0;
                Node<E> p = current;
                lock.lock();
                try {
                    if (p != null || (p = first) != null)
                        for (; p != null && i < n; p = succ(p))
                            if ((a[i] = p.item) != null)
                                i++;
                } finally {
                    lock.unlock();
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
                final ReentrantLock lock = LinkedBlockingDeque.this.lock;
                lock.lock();
                try {
                    Node<E> p;
                    if ((p = current) != null || (p = first) != null)
                        do {
                            e = p.item;
                            p = succ(p);
                        } while (e == null && p != null);
                    if ((current = p) == null)
                        exhausted = true;
                } finally {
                    lock.unlock();
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
