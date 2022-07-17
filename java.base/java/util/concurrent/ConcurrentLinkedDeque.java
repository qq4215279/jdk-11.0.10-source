/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * ConcurrentLinkedDeque
 * @author liuzhen
 * @date 2022/4/24 15:32
 */
public class ConcurrentLinkedDeque<E> extends AbstractCollection<E> implements Deque<E>, java.io.Serializable {

    private static final long serialVersionUID = 876323262645176354L;

    private transient volatile Node<E> head;

    private transient volatile Node<E> tail;

    private static final Node<Object> PREV_TERMINATOR, NEXT_TERMINATOR;

    /**
     *
     * @date 2022/7/16 18:20
     */
    static final class Node<E> {
        volatile Node<E> prev;
        volatile E item;
        volatile Node<E> next;
    }

    @SuppressWarnings("unchecked")
    Node<E> prevTerminator() {
        return (Node<E>)PREV_TERMINATOR;
    }

    @SuppressWarnings("unchecked")
    Node<E> nextTerminator() {
        return (Node<E>)NEXT_TERMINATOR;
    }

    static <E> Node<E> newNode(E item) {
        Node<E> node = new Node<E>();
        ITEM.set(node, item);
        return node;
    }

    // ---------------------------------------------------------------->
    /** 1. BlockingDeque start =========================================================> */
    // 1.1. 添加元素 ---------------------------------------------------------------->
    // 1.1.1. first ----------------------------->
    public void push(E e) {
        addFirst(e);
    }

    public void addFirst(E e) {
        linkFirst(e);
    }

    public boolean offerFirst(E e) {
        linkFirst(e);
        return true;
    }

    public E peekFirst() {
        restart:
        for (; ; ) {
            E item;
            Node<E> first = first(), p = first;
            while ((item = p.item) == null) {
                if (p == (p = p.next))
                    continue restart;
                if (p == null)
                    break;
            }
            // recheck for linearizability
            if (first.prev != null)
                continue restart;
            return item;
        }
    }

    // 1.1.2. last ----------------------------->
    public boolean add(E e) {
        return offerLast(e);
    }

    public boolean addAll(Collection<? extends E> c) {
        if (c == this)
            // As historically specified in AbstractQueue#addAll
            throw new IllegalArgumentException();

        // Copy c into a private chain of Nodes
        Node<E> beginningOfTheEnd = null, last = null;
        for (E e : c) {
            Node<E> newNode = newNode(Objects.requireNonNull(e));
            if (beginningOfTheEnd == null)
                beginningOfTheEnd = last = newNode;
            else {
                NEXT.set(last, newNode);
                PREV.set(newNode, last);
                last = newNode;
            }
        }
        if (beginningOfTheEnd == null)
            return false;

        // Atomically append the chain at the tail of this collection
        restartFromTail:
        for (; ; )
            for (Node<E> t = tail, p = t, q; ; ) {
                if ((q = p.next) != null && (q = (p = q).next) != null)
                    // Check for tail updates every other hop.
                    // If p == q, we are sure to follow tail instead.
                    p = (t != (t = tail)) ? t : q;
                else if (p.prev == p) // NEXT_TERMINATOR
                    continue restartFromTail;
                else {
                    // p is last node
                    PREV.set(beginningOfTheEnd, p); // CAS piggyback
                    if (NEXT.compareAndSet(p, null, beginningOfTheEnd)) {
                        // Successful CAS is the linearization point
                        // for all elements to be added to this deque.
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
                }
            }
    }

    public void addLast(E e) {
        linkLast(e);
    }

    public boolean offer(E e) {
        return offerLast(e);
    }

    public boolean offerLast(E e) {
        linkLast(e);
        return true;
    }

    // 1.2. 删除元素 ---------------------------------------------------------------->
    // 1.2.1. first ----------------------------->
    public E remove() {
        return removeFirst();
    }

    public boolean remove(Object o) {
        return removeFirstOccurrence(o);
    }

    public E removeFirst() {
        return screenNullResult(pollFirst());
    }

    public boolean removeFirstOccurrence(Object o) {
        Objects.requireNonNull(o);
        for (Node<E> p = first(); p != null; p = succ(p)) {
            final E item;
            if ((item = p.item) != null && o.equals(item) && ITEM.compareAndSet(p, item, null)) {
                unlink(p);
                return true;
            }
        }
        return false;
    }

    public E pop() {
        return removeFirst();
    }

    public E pollFirst() {
        restart:
        for (; ; ) {
            for (Node<E> first = first(), p = first; ; ) {
                final E item;
                if ((item = p.item) != null) {
                    // recheck for linearizability
                    if (first.prev != null)
                        continue restart;
                    if (ITEM.compareAndSet(p, item, null)) {
                        unlink(p);
                        return item;
                    }
                }
                if (p == (p = p.next))
                    continue restart;
                if (p == null) {
                    if (first.prev != null)
                        continue restart;
                    return null;
                }
            }
        }
    }

    public E poll() {
        return pollFirst();
    }

    // 1.2.2. ----------------------------->
    public E removeLast() {
        return screenNullResult(pollLast());
    }

    public boolean removeLastOccurrence(Object o) {
        Objects.requireNonNull(o);
        for (Node<E> p = last(); p != null; p = pred(p)) {
            final E item;
            if ((item = p.item) != null && o.equals(item) && ITEM.compareAndSet(p, item, null)) {
                unlink(p);
                return true;
            }
        }
        return false;
    }

    public E pollLast() {
        restart:
        for (; ; ) {
            for (Node<E> last = last(), p = last; ; ) {
                final E item;
                if ((item = p.item) != null) {
                    // recheck for linearizability
                    if (last.next != null)
                        continue restart;
                    if (ITEM.compareAndSet(p, item, null)) {
                        unlink(p);
                        return item;
                    }
                }
                if (p == (p = p.prev))
                    continue restart;
                if (p == null) {
                    if (last.next != null)
                        continue restart;
                    return null;
                }
            }
        }
    }


    // 1.3. 获取元素 ---------------------------------------------------------------->
    // 1.3.1. first ----------------------------->
    public E getFirst() {
        return screenNullResult(peekFirst());
    }

    public E element() {
        return getFirst();
    }

    public E peek() {
        return peekFirst();
    }

    // 1.3.2. last ----------------------------->

    public E getLast() {
        return screenNullResult(peekLast());
    }

    public E peekLast() {
        restart:
        for (; ; ) {
            E item;
            Node<E> last = last(), p = last;
            while ((item = p.item) == null) {
                if (p == (p = p.prev))
                    continue restart;
                if (p == null)
                    break;
            }
            // recheck for linearizability
            if (last.next != null)
                continue restart;
            return item;
        }
    }


    /** 1. BlockingDeque end =========================================================> */

    public boolean contains(Object o) {
        if (o != null) {
            for (Node<E> p = first(); p != null; p = succ(p)) {
                final E item;
                if ((item = p.item) != null && o.equals(item))
                    return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return peekFirst() == null;
    }

    public int size() {
        restart:
        for (; ; ) {
            int count = 0;
            for (Node<E> p = first(); p != null; ) {
                if (p.item != null)
                    if (++count == Integer.MAX_VALUE)
                        break;  // @see Collection.size()
                if (p == (p = p.next))
                    continue restart;
            }
            return count;
        }
    }

    // ---------------------------------------------------------------->

    /** 2. private start ===========================================> */

    /**
     *
     * @date 2022/7/16 18:21
     * @param e
     * @return void
     */
    private void linkFirst(E e) {
        final Node<E> newNode = newNode(Objects.requireNonNull(e));

        restartFromHead:
        for (; ; )
            for (Node<E> h = head, p = h, q; ; ) {
                if ((q = p.prev) != null && (q = (p = q).prev) != null)
                    // Check for head updates every other hop.
                    // If p == q, we are sure to follow head instead.
                    p = (h != (h = head)) ? h : q;
                else if (p.next == p) // PREV_TERMINATOR
                    continue restartFromHead;
                else {
                    // p is first node
                    NEXT.set(newNode, p); // CAS piggyback
                    if (PREV.compareAndSet(p, null, newNode)) {
                        // Successful CAS is the linearization point
                        // for e to become an element of this deque,
                        // and for newNode to become "live".
                        if (p != h) // hop two nodes at a time; failure is OK
                            HEAD.weakCompareAndSet(this, h, newNode);
                        return;
                    }
                    // Lost CAS race to another thread; re-read prev
                }
            }
    }

    /**
     *
     * @date 2022/7/16 18:21
     * @param e
     * @return void
     */
    private void linkLast(E e) {
        final Node<E> newNode = newNode(Objects.requireNonNull(e));

        restartFromTail:
        for (; ; )
            for (Node<E> t = tail, p = t, q; ; ) {
                if ((q = p.next) != null && (q = (p = q).next) != null)
                    // Check for tail updates every other hop.
                    // If p == q, we are sure to follow tail instead.
                    p = (t != (t = tail)) ? t : q;
                else if (p.prev == p) // NEXT_TERMINATOR
                    continue restartFromTail;
                else {
                    // p is last node
                    PREV.set(newNode, p); // CAS piggyback
                    if (NEXT.compareAndSet(p, null, newNode)) {
                        // Successful CAS is the linearization point
                        // for e to become an element of this deque,
                        // and for newNode to become "live".
                        if (p != t) // hop two nodes at a time; failure is OK
                            TAIL.weakCompareAndSet(this, t, newNode);
                        return;
                    }
                    // Lost CAS race to another thread; re-read next
                }
            }
    }

    private static final int HOPS = 2;

    void unlink(Node<E> x) {
        // assert x != null;
        // assert x.item == null;
        // assert x != PREV_TERMINATOR;
        // assert x != NEXT_TERMINATOR;

        final Node<E> prev = x.prev;
        final Node<E> next = x.next;
        if (prev == null) {
            unlinkFirst(x, next);
        } else if (next == null) {
            unlinkLast(x, prev);
        } else {
            Node<E> activePred, activeSucc;
            boolean isFirst, isLast;
            int hops = 1;

            // Find active predecessor
            for (Node<E> p = prev; ; ++hops) {
                if (p.item != null) {
                    activePred = p;
                    isFirst = false;
                    break;
                }
                Node<E> q = p.prev;
                if (q == null) {
                    if (p.next == p)
                        return;
                    activePred = p;
                    isFirst = true;
                    break;
                } else if (p == q)
                    return;
                else
                    p = q;
            }

            // Find active successor
            for (Node<E> p = next; ; ++hops) {
                if (p.item != null) {
                    activeSucc = p;
                    isLast = false;
                    break;
                }
                Node<E> q = p.next;
                if (q == null) {
                    if (p.prev == p)
                        return;
                    activeSucc = p;
                    isLast = true;
                    break;
                } else if (p == q)
                    return;
                else
                    p = q;
            }

            // TODO: better HOP heuristics
            if (hops < HOPS
                // always squeeze out interior deleted nodes
                && (isFirst | isLast))
                return;

            // Squeeze out deleted nodes between activePred and
            // activeSucc, including x.
            skipDeletedSuccessors(activePred);
            skipDeletedPredecessors(activeSucc);

            // Try to gc-unlink, if possible
            if ((isFirst | isLast) &&

                // Recheck expected state of predecessor and successor
                (activePred.next == activeSucc) && (activeSucc.prev == activePred) && (isFirst ? activePred.prev == null : activePred.item != null) &&
                (isLast ? activeSucc.next == null : activeSucc.item != null)) {

                updateHead(); // Ensure x is not reachable from head
                updateTail(); // Ensure x is not reachable from tail

                // Finally, actually gc-unlink
                PREV.setRelease(x, isFirst ? prevTerminator() : x);
                NEXT.setRelease(x, isLast ? nextTerminator() : x);
            }
        }
    }

    private void unlinkFirst(Node<E> first, Node<E> next) {
        // assert first != null;
        // assert next != null;
        // assert first.item == null;
        for (Node<E> o = null, p = next, q; ; ) {
            if (p.item != null || (q = p.next) == null) {
                if (o != null && p.prev != p && NEXT.compareAndSet(first, next, p)) {
                    skipDeletedPredecessors(p);
                    if (first.prev == null && (p.next == null || p.item != null) && p.prev == first) {

                        updateHead(); // Ensure o is not reachable from head
                        updateTail(); // Ensure o is not reachable from tail

                        // Finally, actually gc-unlink
                        NEXT.setRelease(o, o);
                        PREV.setRelease(o, prevTerminator());
                    }
                }
                return;
            } else if (p == q)
                return;
            else {
                o = p;
                p = q;
            }
        }
    }

    private void unlinkLast(Node<E> last, Node<E> prev) {
        // assert last != null;
        // assert prev != null;
        // assert last.item == null;
        for (Node<E> o = null, p = prev, q; ; ) {
            if (p.item != null || (q = p.prev) == null) {
                if (o != null && p.next != p && PREV.compareAndSet(last, prev, p)) {
                    skipDeletedSuccessors(p);
                    if (last.next == null && (p.prev == null || p.item != null) && p.next == last) {

                        updateHead(); // Ensure o is not reachable from head
                        updateTail(); // Ensure o is not reachable from tail

                        // Finally, actually gc-unlink
                        PREV.setRelease(o, o);
                        NEXT.setRelease(o, nextTerminator());
                    }
                }
                return;
            } else if (p == q)
                return;
            else {
                o = p;
                p = q;
            }
        }
    }

    private final void updateHead() {
        // Either head already points to an active node, or we keep
        // trying to cas it to the first node until it does.
        Node<E> h, p, q;
        restartFromHead:
        while ((h = head).item == null && (p = h.prev) != null) {
            for (; ; ) {
                if ((q = p.prev) == null || (q = (p = q).prev) == null) {
                    // It is possible that p is PREV_TERMINATOR,
                    // but if so, the CAS is guaranteed to fail.
                    if (HEAD.compareAndSet(this, h, p))
                        return;
                    else
                        continue restartFromHead;
                } else if (h != head)
                    continue restartFromHead;
                else
                    p = q;
            }
        }
    }

    private final void updateTail() {
        // Either tail already points to an active node, or we keep
        // trying to cas it to the last node until it does.
        Node<E> t, p, q;
        restartFromTail:
        while ((t = tail).item == null && (p = t.next) != null) {
            for (; ; ) {
                if ((q = p.next) == null || (q = (p = q).next) == null) {
                    // It is possible that p is NEXT_TERMINATOR,
                    // but if so, the CAS is guaranteed to fail.
                    if (TAIL.compareAndSet(this, t, p))
                        return;
                    else
                        continue restartFromTail;
                } else if (t != tail)
                    continue restartFromTail;
                else
                    p = q;
            }
        }
    }

    private void skipDeletedPredecessors(Node<E> x) {
        whileActive:
        do {
            Node<E> prev = x.prev;
            // assert prev != null;
            // assert x != NEXT_TERMINATOR;
            // assert x != PREV_TERMINATOR;
            Node<E> p = prev;
            findActive:
            for (; ; ) {
                if (p.item != null)
                    break findActive;
                Node<E> q = p.prev;
                if (q == null) {
                    if (p.next == p)
                        continue whileActive;
                    break findActive;
                } else if (p == q)
                    continue whileActive;
                else
                    p = q;
            }

            // found active CAS target
            if (prev == p || PREV.compareAndSet(x, prev, p))
                return;

        } while (x.item != null || x.next == null);
    }

    private void skipDeletedSuccessors(Node<E> x) {
        whileActive:
        do {
            Node<E> next = x.next;
            // assert next != null;
            // assert x != NEXT_TERMINATOR;
            // assert x != PREV_TERMINATOR;
            Node<E> p = next;
            findActive:
            for (; ; ) {
                if (p.item != null)
                    break findActive;
                Node<E> q = p.next;
                if (q == null) {
                    if (p.prev == p)
                        continue whileActive;
                    break findActive;
                } else if (p == q)
                    continue whileActive;
                else
                    p = q;
            }

            // found active CAS target
            if (next == p || NEXT.compareAndSet(x, next, p))
                return;

        } while (x.item != null || x.prev == null);
    }

    /** 2. private end ===========================================> */

    final Node<E> succ(Node<E> p) {
        // TODO: should we skip deleted nodes here?
        if (p == (p = p.next))
            p = first();
        return p;
    }

    final Node<E> pred(Node<E> p) {
        if (p == (p = p.prev))
            p = last();
        return p;
    }

    Node<E> first() {
        restartFromHead:
        for (; ; )
            for (Node<E> h = head, p = h, q; ; ) {
                if ((q = p.prev) != null && (q = (p = q).prev) != null)
                    // Check for head updates every other hop.
                    // If p == q, we are sure to follow head instead.
                    p = (h != (h = head)) ? h : q;
                else if (p == h
                         // It is possible that p is PREV_TERMINATOR,
                         // but if so, the CAS is guaranteed to fail.
                         || HEAD.compareAndSet(this, h, p))
                    return p;
                else
                    continue restartFromHead;
            }
    }

    Node<E> last() {
        restartFromTail:
        for (; ; )
            for (Node<E> t = tail, p = t, q; ; ) {
                if ((q = p.next) != null && (q = (p = q).next) != null)
                    // Check for tail updates every other hop.
                    // If p == q, we are sure to follow tail instead.
                    p = (t != (t = tail)) ? t : q;
                else if (p == t
                         // It is possible that p is NEXT_TERMINATOR,
                         // but if so, the CAS is guaranteed to fail.
                         || TAIL.compareAndSet(this, t, p))
                    return p;
                else
                    continue restartFromTail;
            }
    }

    // ---------------------------------------------------------------->


    private E screenNullResult(E v) {
        if (v == null)
            throw new NoSuchElementException();
        return v;
    }

    public ConcurrentLinkedDeque() {
        head = tail = new Node<E>();
    }

    public ConcurrentLinkedDeque(Collection<? extends E> c) {
        // Copy c into a private chain of Nodes
        Node<E> h = null, t = null;
        for (E e : c) {
            Node<E> newNode = newNode(Objects.requireNonNull(e));
            if (h == null)
                h = t = newNode;
            else {
                NEXT.set(t, newNode);
                PREV.set(newNode, t);
                t = newNode;
            }
        }
        initHeadTail(h, t);
    }

    private void initHeadTail(Node<E> h, Node<E> t) {
        if (h == t) {
            if (h == null)
                h = t = new Node<E>();
            else {
                // Avoid edge case of a single Node with non-null item.
                Node<E> newNode = new Node<E>();
                NEXT.set(t, newNode);
                PREV.set(newNode, t);
                t = newNode;
            }
        }
        head = h;
        tail = t;
    }

    public void clear() {
        while (pollFirst() != null)
            ;
    }

    public String toString() {
        String[] a = null;
        restart:
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
                    continue restart;
            }

            if (size == 0)
                return "[]";

            return Helpers.toString(a, size, charLength);
        }
    }

    private Object[] toArrayInternal(Object[] a) {
        Object[] x = a;
        restart:
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
                    continue restart;
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
        if (a == null)
            throw new NullPointerException();
        return (T[])toArrayInternal(a);
    }

    public Iterator<E> iterator() {
        return new Itr();
    }

    public Iterator<E> descendingIterator() {
        return new DescendingItr();
    }

    public Spliterator<E> spliterator() {
        return new CLDSpliterator();
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
            @SuppressWarnings("unchecked") Node<E> newNode = newNode((E)item);
            if (h == null)
                h = t = newNode;
            else {
                NEXT.set(t, newNode);
                PREV.set(newNode, t);
                t = newNode;
            }
        }
        initHeadTail(h, t);
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

    private boolean bulkRemove(Predicate<? super E> filter) {
        boolean removed = false;
        for (Node<E> p = first(), succ; p != null; p = succ) {
            succ = succ(p);
            final E item;
            if ((item = p.item) != null && filter.test(item) && ITEM.compareAndSet(p, item, null)) {
                unlink(p);
                removed = true;
            }
        }
        return removed;
    }

    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        E item;
        for (Node<E> p = first(); p != null; p = succ(p))
            if ((item = p.item) != null)
                action.accept(item);
    }

    // VarHandle mechanics
    private static final VarHandle HEAD;
    private static final VarHandle TAIL;
    private static final VarHandle PREV;
    private static final VarHandle NEXT;
    private static final VarHandle ITEM;

    static {
        PREV_TERMINATOR = new Node<Object>();
        PREV_TERMINATOR.next = PREV_TERMINATOR;
        NEXT_TERMINATOR = new Node<Object>();
        NEXT_TERMINATOR.prev = NEXT_TERMINATOR;
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            HEAD = l.findVarHandle(ConcurrentLinkedDeque.class, "head", Node.class);
            TAIL = l.findVarHandle(ConcurrentLinkedDeque.class, "tail", Node.class);
            PREV = l.findVarHandle(Node.class, "prev", Node.class);
            NEXT = l.findVarHandle(Node.class, "next", Node.class);
            ITEM = l.findVarHandle(Node.class, "item", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     *
     */
    private abstract class AbstractItr implements Iterator<E> {
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
         * Node returned by most recent call to next. Needed by remove.
         * Reset to null if this element is deleted by a call to remove.
         */
        private Node<E> lastRet;

        abstract Node<E> startNode();

        abstract Node<E> nextNode(Node<E> p);

        AbstractItr() {
            advance();
        }

        /**
         * Sets nextNode and nextItem to next valid node, or to null
         * if no such.
         */
        private void advance() {
            lastRet = nextNode;

            Node<E> p = (nextNode == null) ? startNode() : nextNode(nextNode);
            for (; ; p = nextNode(p)) {
                if (p == null) {
                    // might be at active end or TERMINATOR node; both are OK
                    nextNode = null;
                    nextItem = null;
                    break;
                }
                final E item;
                if ((item = p.item) != null) {
                    nextNode = p;
                    nextItem = item;
                    break;
                }
            }
        }

        public boolean hasNext() {
            return nextItem != null;
        }

        public E next() {
            E item = nextItem;
            if (item == null)
                throw new NoSuchElementException();
            advance();
            return item;
        }

        public void remove() {
            Node<E> l = lastRet;
            if (l == null)
                throw new IllegalStateException();
            l.item = null;
            unlink(l);
            lastRet = null;
        }
    }

    /**
     *
     */
    private class Itr extends AbstractItr {
        Itr() {
        }                        // prevent access constructor creation

        Node<E> startNode() {
            return first();
        }

        Node<E> nextNode(Node<E> p) {
            return succ(p);
        }
    }

    /**
     *
     */
    private class DescendingItr extends AbstractItr {
        DescendingItr() {
        }              // prevent access constructor creation

        Node<E> startNode() {
            return last();
        }

        Node<E> nextNode(Node<E> p) {
            return pred(p);
        }
    }

    /**
     *
     */
    final class CLDSpliterator implements Spliterator<E> {
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
            Node<E> p;
            if ((p = current()) != null) {
                current = null;
                exhausted = true;
                do {
                    final E e;
                    if ((e = p.item) != null)
                        action.accept(e);
                    if (p == (p = p.next))
                        p = first();
                } while (p != null);
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
}
