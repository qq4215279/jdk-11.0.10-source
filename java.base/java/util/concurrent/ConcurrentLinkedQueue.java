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
 * Written by Doug Lea and Martin Buchholz with assistance from members of
 * JCP JSR-166 Expert Group and released to the public domain, as explained
 * at http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractQueue;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * AQS内部的阻塞队列实现原理：基于双向链表，通过对head/tail进行CAS操作，实现入队和出队。
 * ConcurrentLinkedQueue 的实现原理和AQS 内部的阻塞队列类似：同样是基于 CAS，同样是通过head/tail指针记录队列头部和尾部，但还是有稍许差别：
 * 首先，它是一个单向链表，
 * 其次，在AQS的阻塞队列中，每次入队后，tail一定后移一个位置；每次出队，head一定后移一个位置，以保证head指向队列头部，tail指向链表尾部。
 * 但在ConcurrentLinkedQueue中，head/tail的更新可能落后于节点的入队和出队，因为它不是直接对 head/tail指针进行 CAS操作的，而是对 Node中的 item进行操作。
 *
 * @author liuzhen
 * @date 2022/4/16 10:42
 * @return
 */
public class ConcurrentLinkedQueue<E> extends AbstractQueue<E> implements Queue<E>, java.io.Serializable {
    private static final long serialVersionUID = 196745693267521676L;

    static final class Node<E> {
        volatile E item;
        volatile Node<E> next;

        Node(E item) {
            ITEM.set(this, item);
        }

        Node() {
        }

        void appendRelaxed(Node<E> next) {
            // assert next != null;
            // assert this.next == null;
            NEXT.set(this, next);
        }

        boolean casItem(E cmp, E val) {
            // assert item == cmp || item == null;
            // assert cmp != null;
            // assert val == null;
            return ITEM.compareAndSet(this, cmp, val);
        }
    }

    transient volatile Node<E> head;

    private transient volatile Node<E> tail;

    public ConcurrentLinkedQueue() {
        head = tail = new Node<E>();
    }

    public ConcurrentLinkedQueue(Collection<? extends E> c) {
        Node<E> h = null, t = null;
        for (E e : c) {
            Node<E> newNode = new Node<E>(Objects.requireNonNull(e));
            if (h == null)
                h = t = newNode;
            else
                t.appendRelaxed(t = newNode);
        }
        if (h == null)
            h = t = new Node<E>();
        head = h;
        tail = t;
    }

    // Have to override just to update the javadoc

    public boolean add(E e) {
        return offer(e);
    }

    final void updateHead(Node<E> h, Node<E> p) {
        // assert h != null && p != null && (h == p || h.item == null);
        if (h != p && HEAD.compareAndSet(this, h, p))
            NEXT.setRelease(h, h);
    }

    final Node<E> succ(Node<E> p) {
        if (p == (p = p.next))
            p = head;
        return p;
    }

    private boolean tryCasSuccessor(Node<E> pred, Node<E> c, Node<E> p) {
        // assert p != null;
        // assert c.item == null;
        // assert c != p;
        if (pred != null)
            return NEXT.compareAndSet(pred, c, p);
        if (HEAD.compareAndSet(this, c, p)) {
            NEXT.setRelease(c, c);
            return true;
        }
        return false;
    }

    private Node<E> skipDeadNodes(Node<E> pred, Node<E> c, Node<E> p, Node<E> q) {
        // assert pred != c;
        // assert p != q;
        // assert c.item == null;
        // assert p.item == null;
        if (q == null) {
            // Never unlink trailing node.
            if (c == p)
                return pred;
            q = p;
        }
        return (tryCasSuccessor(pred, c, q) && (pred == null || ITEM.get(pred) != null)) ? pred : p;
    }

    /**
     * 最后总结一下入队列的两个关键点：
     * 1. 即使tail指针没有移动，只要对p的next指针成功进行CAS操作，就算成功入队列。
     * 2. 只有当 p != tail的时候，才会后移tail指针。也就是说，每连续追加2个节点，才后移1次tail指针。即使CAS失败也没关系，可以由下1个线程来移动tail指针。
     * @author liuzhen
     * @date 2022/4/16 10:45
     * @param e
     * @return boolean
     */
    public boolean offer(E e) {
        final Node<E> newNode = new Node<E>(Objects.requireNonNull(e));

        for (Node<E> t = tail, p = t; ; ) {
            Node<E> q = p.next;
            if (q == null) {
                // p is last node
                // 对tail的next指针而不是对tail指针执行CAS操作
                if (NEXT.compareAndSet(p, null, newNode)) {
                    // Successful CAS is the linearization point
                    // for e to become an element of this queue,
                    // and for newNode to become "live".
                    // 每入列两个节点，后移一次tail指针 失败也无所谓了。
                    if (p != t) // hop two nodes at a time; failure is OK
                        TAIL.weakCompareAndSet(this, t, newNode);
                    return true;
                }
                // Lost CAS race to another thread; re-read next
            } else if (p == q)
                // We have fallen off list.  If tail is unchanged, it
                // will also be off-list, in which case we need to
                // jump to head, from which all live nodes are always
                // reachable.  Else the new tail is a better bet.
                // 已经到达队列尾部
                p = (t != (t = tail)) ? t : head;
            else
                // Check for tail updates after two hops.
                // 后移p指针
                p = (p != t && t != (t = tail)) ? t : q;
        }
    }

    /**
     * 最后总结一下出队列的关键点：
     * 1. 出队列的判断并非观察 tail 指针的位置，而是依赖于 head 指针后续的节点是否为NULL这一条件。
     * 2. 只要对节点的item执行CAS操作，置为NULL成功，则出队列成功。即使head指针没有成功移动，也可以由下1个线程继续完成。
     * @author liuzhen
     * @date 2022/4/16 10:49
     * @param
     * @return E
     */
    public E poll() {
        restartFromHead:
        for (; ; ) {
            for (Node<E> h = head, p = h, q; ; p = q) {
                final E item;
                // 注意：在出队列的时候，并没有移动head指针，而是吧item置为null
                if ((item = p.item) != null && p.casItem(item, null)) {
                    // Successful CAS is the linearization point
                    // for item to be removed from this queue.
                    if (p != h) // hop two nodes at a time
                        // 每产生2个NULL节点，才把head指针后移2位
                        updateHead(h, ((q = p.next) != null) ? q : p);
                    return item;
                } else if ((q = p.next) == null) {
                    updateHead(h, p);
                    return null;
                } else if (p == q)
                    continue restartFromHead;
            }
        }
    }

    public E peek() {
        restartFromHead:
        for (; ; ) {
            for (Node<E> h = head, p = h, q; ; p = q) {
                final E item;
                if ((item = p.item) != null || (q = p.next) == null) {
                    updateHead(h, p);
                    return item;
                } else if (p == q)
                    continue restartFromHead;
            }
        }
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/16 11:05
     * @param
     * @return boolean
     */
    public boolean isEmpty() {
        // 寻找第一个不是null的节点
        return first() == null;
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/16 11:05
     * @param
     * @return java.util.concurrent.ConcurrentLinkedQueue.Node<E>
     */
    Node<E> first() {
        restartFromHead:
        for (; ; ) {
            // 从head指针开始遍历，寻找第一个不是null的节点
            for (Node<E> h = head, p = h, q; ; p = q) {
                boolean hasItem = (p.item != null);
                if (hasItem || (q = p.next) == null) {
                    updateHead(h, p);
                    return hasItem ? p : null;
                } else if (p == q)
                    continue restartFromHead;
            }
        }
    }

    public int size() {
        restartFromHead:
        for (; ; ) {
            int count = 0;
            for (Node<E> p = first(); p != null; ) {
                if (p.item != null)
                    if (++count == Integer.MAX_VALUE)
                        break;  // @see Collection.size()
                if (p == (p = p.next))
                    continue restartFromHead;
            }
            return count;
        }
    }

    public boolean contains(Object o) {
        if (o == null)
            return false;
        restartFromHead:
        for (; ; ) {
            for (Node<E> p = head, pred = null; p != null; ) {
                Node<E> q = p.next;
                final E item;
                if ((item = p.item) != null) {
                    if (o.equals(item))
                        return true;
                    pred = p;
                    p = q;
                    continue;
                }
                for (Node<E> c = p; ; q = p.next) {
                    if (q == null || q.item != null) {
                        pred = skipDeadNodes(pred, c, p, q);
                        p = q;
                        break;
                    }
                    if (p == (p = q))
                        continue restartFromHead;
                }
            }
            return false;
        }
    }

    public boolean remove(Object o) {
        if (o == null)
            return false;
        restartFromHead:
        for (; ; ) {
            for (Node<E> p = head, pred = null; p != null; ) {
                Node<E> q = p.next;
                final E item;
                if ((item = p.item) != null) {
                    if (o.equals(item) && p.casItem(item, null)) {
                        skipDeadNodes(pred, p, p, q);
                        return true;
                    }
                    pred = p;
                    p = q;
                    continue;
                }
                for (Node<E> c = p; ; q = p.next) {
                    if (q == null || q.item != null) {
                        pred = skipDeadNodes(pred, c, p, q);
                        p = q;
                        break;
                    }
                    if (p == (p = q))
                        continue restartFromHead;
                }
            }
            return false;
        }
    }

    public boolean addAll(Collection<? extends E> c) {
        if (c == this)
            // As historically specified in AbstractQueue#addAll
            throw new IllegalArgumentException();

        // Copy c into a private chain of Nodes
        Node<E> beginningOfTheEnd = null, last = null;
        for (E e : c) {
            Node<E> newNode = new Node<E>(Objects.requireNonNull(e));
            if (beginningOfTheEnd == null)
                beginningOfTheEnd = last = newNode;
            else
                last.appendRelaxed(last = newNode);
        }
        if (beginningOfTheEnd == null)
            return false;

        // Atomically append the chain at the tail of this collection
        for (Node<E> t = tail, p = t; ; ) {
            Node<E> q = p.next;
            if (q == null) {
                // p is last node
                if (NEXT.compareAndSet(p, null, beginningOfTheEnd)) {
                    // Successful CAS is the linearization point
                    // for all elements to be added to this queue.
                    if (!TAIL.weakCompareAndSet(this, t, last)) {
                        // Try a little harder to update tail,
                        // since we may be adding many elements.
                        t = tail;
                        if (last.next == null)
                            TAIL.weakCompareAndSet(this, t, last);
                    }
                    return true;
                }
                // Lost CAS race to another thread; re-read next
            } else if (p == q)
                // We have fallen off list.  If tail is unchanged, it
                // will also be off-list, in which case we need to
                // jump to head, from which all live nodes are always
                // reachable.  Else the new tail is a better bet.
                p = (t != (t = tail)) ? t : head;
            else
                // Check for tail updates after two hops.
                p = (p != t && t != (t = tail)) ? t : q;
        }
    }

    public String toString() {
        String[] a = null;
        restartFromHead:
        for (; ; ) {
            int charLength = 0;
            int size = 0;
            for (Node<E> p = first(); p != null; ) {
                final E item;
                if ((item = p.item) != null) {
                    if (a == null)
                        a = new String[4];
                    else if (size == a.length)
                        a = Arrays.copyOf(a, 2 * size);
                    String s = item.toString();
                    a[size++] = s;
                    charLength += s.length();
                }
                if (p == (p = p.next))
                    continue restartFromHead;
            }

            if (size == 0)
                return "[]";

            return Helpers.toString(a, size, charLength);
        }
    }

    private Object[] toArrayInternal(Object[] a) {
        Object[] x = a;
        restartFromHead:
        for (; ; ) {
            int size = 0;
            for (Node<E> p = first(); p != null; ) {
                final E item;
                if ((item = p.item) != null) {
                    if (x == null)
                        x = new Object[4];
                    else if (size == x.length)
                        x = Arrays.copyOf(x, 2 * (size + 4));
                    x[size++] = item;
                }
                if (p == (p = p.next))
                    continue restartFromHead;
            }
            if (x == null)
                return new Object[0];
            else if (a != null && size <= a.length) {
                if (a != x)
                    System.arraycopy(x, 0, a, 0, size);
                if (size < a.length)
                    a[size] = null;
                return a;
            }
            return (size == x.length) ? x : Arrays.copyOf(x, size);
        }
    }

    public Object[] toArray() {
        return toArrayInternal(null);
    }

    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        Objects.requireNonNull(a);
        return (T[])toArrayInternal(a);
    }

    public Iterator<E> iterator() {
        return new Itr();
    }

    private class Itr implements Iterator<E> {
        /**
         * Next node to return item for.
         */
        private Node<E> nextNode;

        /**
         * nextItem holds on to item fields because once we claim
         * that an element exists in hasNext(), we must return it in
         * the following next() call even if it was in the process of
         * being removed when hasNext() was called.
         */
        private E nextItem;

        /**
         * Node of the last returned item, to support remove.
         */
        private Node<E> lastRet;

        Itr() {
            restartFromHead:
            for (; ; ) {
                Node<E> h, p, q;
                for (p = h = head; ; p = q) {
                    final E item;
                    if ((item = p.item) != null) {
                        nextNode = p;
                        nextItem = item;
                        break;
                    } else if ((q = p.next) == null)
                        break;
                    else if (p == q)
                        continue restartFromHead;
                }
                updateHead(h, p);
                return;
            }
        }

        public boolean hasNext() {
            return nextItem != null;
        }

        public E next() {
            final Node<E> pred = nextNode;
            if (pred == null)
                throw new NoSuchElementException();
            // assert nextItem != null;
            lastRet = pred;
            E item = null;

            for (Node<E> p = succ(pred), q; ; p = q) {
                if (p == null || (item = p.item) != null) {
                    nextNode = p;
                    E x = nextItem;
                    nextItem = item;
                    return x;
                }
                // unlink deleted nodes
                if ((q = succ(p)) != null)
                    NEXT.compareAndSet(pred, p, q);
            }
        }

        // Default implementation of forEachRemaining is "good enough".

        public void remove() {
            Node<E> l = lastRet;
            if (l == null)
                throw new IllegalStateException();
            // rely on a future traversal to relink.
            l.item = null;
            lastRet = null;
        }
    }

    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {

        // Write out any hidden stuff
        s.defaultWriteObject();

        // Write out all elements in the proper order.
        for (Node<E> p = first(); p != null; p = succ(p)) {
            final E item;
            if ((item = p.item) != null)
                s.writeObject(item);
        }

        // Use trailing null as sentinel
        s.writeObject(null);
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();

        // Read in elements until trailing null sentinel found
        Node<E> h = null, t = null;
        for (Object item; (item = s.readObject()) != null; ) {
            @SuppressWarnings("unchecked") Node<E> newNode = new Node<E>((E)item);
            if (h == null)
                h = t = newNode;
            else
                t.appendRelaxed(t = newNode);
        }
        if (h == null)
            h = t = new Node<E>();
        head = h;
        tail = t;
    }

    final class CLQSpliterator implements Spliterator<E> {
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        Node<E> current;    // current node; null until initialized
        int batch;          // batch size for splits
        boolean exhausted;  // true when no more nodes

        public Spliterator<E> trySplit() {
            Node<E> p, q;
            if ((p = current()) == null || (q = p.next) == null)
                return null;
            int i = 0, n = batch = Math.min(batch + 1, MAX_BATCH);
            Object[] a = null;
            do {
                final E e;
                if ((e = p.item) != null) {
                    if (a == null)
                        a = new Object[n];
                    a[i++] = e;
                }
                if (p == (p = q))
                    p = first();
            } while (p != null && (q = p.next) != null && i < n);
            setCurrent(p);
            return (i == 0) ? null : Spliterators.spliterator(a, 0, i, (Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.CONCURRENT));
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            final Node<E> p;
            if ((p = current()) != null) {
                current = null;
                exhausted = true;
                forEachFrom(action, p);
            }
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            Node<E> p;
            if ((p = current()) != null) {
                E e;
                do {
                    e = p.item;
                    if (p == (p = p.next))
                        p = first();
                } while (e == null && p != null);
                setCurrent(p);
                if (e != null) {
                    action.accept(e);
                    return true;
                }
            }
            return false;
        }

        private void setCurrent(Node<E> p) {
            if ((current = p) == null)
                exhausted = true;
        }

        private Node<E> current() {
            Node<E> p;
            if ((p = current) == null && !exhausted)
                setCurrent(p = first());
            return p;
        }

        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        public int characteristics() {
            return (Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.CONCURRENT);
        }
    }

    @Override
    public Spliterator<E> spliterator() {
        return new CLQSpliterator();
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

    public void clear() {
        bulkRemove(e -> true);
    }

    private static final int MAX_HOPS = 8;

    private boolean bulkRemove(Predicate<? super E> filter) {
        boolean removed = false;
        restartFromHead:
        for (; ; ) {
            int hops = MAX_HOPS;
            // c will be CASed to collapse intervening dead nodes between
            // pred (or head if null) and p.
            for (Node<E> p = head, c = p, pred = null, q; p != null; p = q) {
                q = p.next;
                final E item;
                boolean pAlive;
                if (pAlive = ((item = p.item) != null)) {
                    if (filter.test(item)) {
                        if (p.casItem(item, null))
                            removed = true;
                        pAlive = false;
                    }
                }
                if (pAlive || q == null || --hops == 0) {
                    // p might already be self-linked here, but if so:
                    // - CASing head will surely fail
                    // - CASing pred's next will be useless but harmless.
                    if ((c != p && !tryCasSuccessor(pred, c, c = p)) || pAlive) {
                        // if CAS failed or alive, abandon old pred
                        hops = MAX_HOPS;
                        pred = p;
                        c = q;
                    }
                } else if (p == q)
                    continue restartFromHead;
            }
            return removed;
        }
    }

    void forEachFrom(Consumer<? super E> action, Node<E> p) {
        for (Node<E> pred = null; p != null; ) {
            Node<E> q = p.next;
            final E item;
            if ((item = p.item) != null) {
                action.accept(item);
                pred = p;
                p = q;
                continue;
            }
            for (Node<E> c = p; ; q = p.next) {
                if (q == null || q.item != null) {
                    pred = skipDeadNodes(pred, c, p, q);
                    p = q;
                    break;
                }
                if (p == (p = q)) {
                    pred = null;
                    p = head;
                    break;
                }
            }
        }
    }

    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        forEachFrom(action, head);
    }

    // VarHandle mechanics
    private static final VarHandle HEAD;
    private static final VarHandle TAIL;
    static final VarHandle ITEM;
    static final VarHandle NEXT;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            HEAD = l.findVarHandle(ConcurrentLinkedQueue.class, "head", Node.class);
            TAIL = l.findVarHandle(ConcurrentLinkedQueue.class, "tail", Node.class);
            ITEM = l.findVarHandle(Node.class, "item", Object.class);
            NEXT = l.findVarHandle(Node.class, "next", Node.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
