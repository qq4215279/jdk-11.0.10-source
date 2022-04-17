/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent;

import java.lang.ref.WeakReference;
import java.util.AbstractQueue;
import java.util.Arrays;
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

public class ArrayBlockingQueue<E> extends AbstractQueue<E> implements BlockingQueue<E>, java.io.Serializable {

    private static final long serialVersionUID = -817911632652898426L;

    final Object[] items;

    /**
     * 队头指针
     */
    int takeIndex;
    /**
     * 队尾指针
     */
    int putIndex;

    int count;

    /*
     * Concurrency control uses the classic two-condition algorithm
     * found in any textbook.
     */

    /**
     * 核心为1个锁外加两个条件
     */
    final ReentrantLock lock;
    private final Condition notEmpty;
    private final Condition notFull;

    transient Itrs itrs;

    // Internal helper methods

    static final int inc(int i, int modulus) {
        if (++i >= modulus)
            i = 0;
        return i;
    }

    static final int dec(int i, int modulus) {
        if (--i < 0)
            i = modulus - 1;
        return i;
    }

    @SuppressWarnings("unchecked")
    final E itemAt(int i) {
        return (E)items[i];
    }

    @SuppressWarnings("unchecked")
    static <E> E itemAt(Object[] items, int i) {
        return (E)items[i];
    }

    private E dequeue() {
        // assert lock.isHeldByCurrentThread();
        // assert lock.getHoldCount() == 1;
        // assert items[takeIndex] != null;
        final Object[] items = this.items;
        @SuppressWarnings("unchecked") E e = (E)items[takeIndex];
        items[takeIndex] = null;
        if (++takeIndex == items.length)
            takeIndex = 0;
        count--;
        if (itrs != null)
            itrs.elementDequeued();
        notFull.signal();
        return e;
    }

    void removeAt(final int removeIndex) {
        // assert lock.isHeldByCurrentThread();
        // assert lock.getHoldCount() == 1;
        // assert items[removeIndex] != null;
        // assert removeIndex >= 0 && removeIndex < items.length;
        final Object[] items = this.items;
        if (removeIndex == takeIndex) {
            // removing front item; just advance
            items[takeIndex] = null;
            if (++takeIndex == items.length)
                takeIndex = 0;
            count--;
            if (itrs != null)
                itrs.elementDequeued();
        } else {
            // an "interior" remove

            // slide over all others up through putIndex.
            for (int i = removeIndex, putIndex = this.putIndex; ; ) {
                int pred = i;
                if (++i == items.length)
                    i = 0;
                if (i == putIndex) {
                    items[pred] = null;
                    this.putIndex = pred;
                    break;
                }
                items[pred] = items[i];
            }
            count--;
            if (itrs != null)
                itrs.removedAt(removeIndex);
        }
        notFull.signal();
    }

    public ArrayBlockingQueue(int capacity) {
        this(capacity, false);
    }

    public ArrayBlockingQueue(int capacity, boolean fair) {
        if (capacity <= 0)
            throw new IllegalArgumentException();
        this.items = new Object[capacity];
        lock = new ReentrantLock(fair);
        notEmpty = lock.newCondition();
        notFull = lock.newCondition();
    }

    public ArrayBlockingQueue(int capacity, boolean fair, Collection<? extends E> c) {
        this(capacity, fair);

        final ReentrantLock lock = this.lock;
        lock.lock(); // Lock only for visibility, not mutual exclusion
        try {
            final Object[] items = this.items;
            int i = 0;
            try {
                for (E e : c)
                    items[i++] = Objects.requireNonNull(e);
            } catch (ArrayIndexOutOfBoundsException ex) {
                throw new IllegalArgumentException();
            }
            count = i;
            putIndex = (i == capacity) ? 0 : i;
        } finally {
            lock.unlock();
        }
    }

    public boolean add(E e) {
        return super.add(e);
    }

    public boolean offer(E e) {
        Objects.requireNonNull(e);
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (count == items.length)
                return false;
            else {
                enqueue(e);
                return true;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * @param e
     * @return void
     * @author liuzhen
     * @date 2022/4/15 22:47
     */
    public void put(E e) throws InterruptedException {
        Objects.requireNonNull(e);
        final ReentrantLock lock = this.lock;
        // 可中断的lock
        lock.lockInterruptibly();
        try {
            // 若队列满，则阻塞
            while (count == items.length)
                notFull.await();
            enqueue(e);
        } finally {
            lock.unlock();
        }
    }

    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {

        Objects.requireNonNull(e);
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (count == items.length) {
                if (nanos <= 0L)
                    return false;
                nanos = notFull.awaitNanos(nanos);
            }
            enqueue(e);
            return true;
        } finally {
            lock.unlock();
        }
    }

    private void enqueue(E e) {
        // assert lock.isHeldByCurrentThread();
        // assert lock.getHoldCount() == 1;
        // assert items[putIndex] == null;
        final Object[] items = this.items;
        items[putIndex] = e;
        if (++putIndex == items.length)
            putIndex = 0;
        count++;
        // 当将数据put 到队列后，通知非空条件
        notEmpty.signal();
    }

    public E poll() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return (count == 0) ? null : dequeue();
        } finally {
            lock.unlock();
        }
    }

    public E take() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (count == 0)
                notEmpty.await();
            return dequeue();
        } finally {
            lock.unlock();
        }
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (count == 0) {
                if (nanos <= 0L)
                    return null;
                nanos = notEmpty.awaitNanos(nanos);
            }
            return dequeue();
        } finally {
            lock.unlock();
        }
    }

    public E peek() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return itemAt(takeIndex); // null when queue is empty
        } finally {
            lock.unlock();
        }
    }

    // this doc comment is overridden to remove the reference to collections
    // greater in size than Integer.MAX_VALUE
    public int size() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    // this doc comment is a modified copy of the inherited doc comment,
    // without the reference to unlimited queues.
    public int remainingCapacity() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return items.length - count;
        } finally {
            lock.unlock();
        }
    }

    public boolean remove(Object o) {
        if (o == null)
            return false;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (count > 0) {
                final Object[] items = this.items;
                for (int i = takeIndex, end = putIndex, to = (i < end) ? end : items.length; ; i = 0, to = end) {
                    for (; i < to; i++)
                        if (o.equals(items[i])) {
                            removeAt(i);
                            return true;
                        }
                    if (to == end)
                        break;
                }
            }
            return false;
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
            if (count > 0) {
                final Object[] items = this.items;
                for (int i = takeIndex, end = putIndex, to = (i < end) ? end : items.length; ; i = 0, to = end) {
                    for (; i < to; i++)
                        if (o.equals(items[i]))
                            return true;
                    if (to == end)
                        break;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public Object[] toArray() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final Object[] items = this.items;
            final int end = takeIndex + count;
            final Object[] a = Arrays.copyOfRange(items, takeIndex, end);
            if (end != putIndex)
                System.arraycopy(items, 0, a, items.length - takeIndex, putIndex);
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
            final Object[] items = this.items;
            final int count = this.count;
            final int firstLeg = Math.min(items.length - takeIndex, count);
            if (a.length < count) {
                a = (T[])Arrays.copyOfRange(items, takeIndex, takeIndex + count, a.getClass());
            } else {
                System.arraycopy(items, takeIndex, a, 0, firstLeg);
                if (a.length > count)
                    a[count] = null;
            }
            if (firstLeg < count)
                System.arraycopy(items, 0, a, firstLeg, putIndex);
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
            int k;
            if ((k = count) > 0) {
                circularClear(items, takeIndex, putIndex);
                takeIndex = putIndex;
                count = 0;
                if (itrs != null)
                    itrs.queueIsEmpty();
                for (; k > 0 && lock.hasWaiters(notFull); k--)
                    notFull.signal();
            }
        } finally {
            lock.unlock();
        }
    }

    private static void circularClear(Object[] items, int i, int end) {
        // assert 0 <= i && i < items.length;
        // assert 0 <= end && end < items.length;
        for (int to = (i < end) ? end : items.length; ; i = 0, to = end) {
            for (; i < to; i++)
                items[i] = null;
            if (to == end)
                break;
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
        final Object[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int n = Math.min(maxElements, count);
            int take = takeIndex;
            int i = 0;
            try {
                while (i < n) {
                    @SuppressWarnings("unchecked") E e = (E)items[take];
                    c.add(e);
                    items[take] = null;
                    if (++take == items.length)
                        take = 0;
                    i++;
                }
                return n;
            } finally {
                // Restore invariants even if c.add() threw
                if (i > 0) {
                    count -= i;
                    takeIndex = take;
                    if (itrs != null) {
                        if (count == 0)
                            itrs.queueIsEmpty();
                        else if (i > take)
                            itrs.takeIndexWrapped();
                    }
                    for (; i > 0 && lock.hasWaiters(notFull); i--)
                        notFull.signal();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public Iterator<E> iterator() {
        return new Itr();
    }

    class Itrs {

        private class Node extends WeakReference<Itr> {
            Node next;

            Node(Itr iterator, Node next) {
                super(iterator);
                this.next = next;
            }
        }

        int cycles;

        private Node head;

        private Node sweeper;

        private static final int SHORT_SWEEP_PROBES = 4;
        private static final int LONG_SWEEP_PROBES = 16;

        Itrs(Itr initial) {
            register(initial);
        }

        void doSomeSweeping(boolean tryHarder) {
            // assert lock.isHeldByCurrentThread();
            // assert head != null;
            int probes = tryHarder ? LONG_SWEEP_PROBES : SHORT_SWEEP_PROBES;
            Node o, p;
            final Node sweeper = this.sweeper;
            boolean passedGo;   // to limit search to one full sweep

            if (sweeper == null) {
                o = null;
                p = head;
                passedGo = true;
            } else {
                o = sweeper;
                p = o.next;
                passedGo = false;
            }

            for (; probes > 0; probes--) {
                if (p == null) {
                    if (passedGo)
                        break;
                    o = null;
                    p = head;
                    passedGo = true;
                }
                final Itr it = p.get();
                final Node next = p.next;
                if (it == null || it.isDetached()) {
                    // found a discarded/exhausted iterator
                    probes = LONG_SWEEP_PROBES; // "try harder"
                    // unlink p
                    p.clear();
                    p.next = null;
                    if (o == null) {
                        head = next;
                        if (next == null) {
                            // We've run out of iterators to track; retire
                            itrs = null;
                            return;
                        }
                    } else
                        o.next = next;
                } else {
                    o = p;
                }
                p = next;
            }

            this.sweeper = (p == null) ? null : o;
        }

        void register(Itr itr) {
            // assert lock.isHeldByCurrentThread();
            head = new Node(itr, head);
        }

        void takeIndexWrapped() {
            // assert lock.isHeldByCurrentThread();
            cycles++;
            for (Node o = null, p = head; p != null; ) {
                final Itr it = p.get();
                final Node next = p.next;
                if (it == null || it.takeIndexWrapped()) {
                    // unlink p
                    // assert it == null || it.isDetached();
                    p.clear();
                    p.next = null;
                    if (o == null)
                        head = next;
                    else
                        o.next = next;
                } else {
                    o = p;
                }
                p = next;
            }
            if (head == null)   // no more iterators to track
                itrs = null;
        }

        void removedAt(int removedIndex) {
            for (Node o = null, p = head; p != null; ) {
                final Itr it = p.get();
                final Node next = p.next;
                if (it == null || it.removedAt(removedIndex)) {
                    // unlink p
                    // assert it == null || it.isDetached();
                    p.clear();
                    p.next = null;
                    if (o == null)
                        head = next;
                    else
                        o.next = next;
                } else {
                    o = p;
                }
                p = next;
            }
            if (head == null)   // no more iterators to track
                itrs = null;
        }

        void queueIsEmpty() {
            // assert lock.isHeldByCurrentThread();
            for (Node p = head; p != null; p = p.next) {
                Itr it = p.get();
                if (it != null) {
                    p.clear();
                    it.shutdown();
                }
            }
            head = null;
            itrs = null;
        }

        /**
         * Called whenever an element has been dequeued (at takeIndex).
         */
        void elementDequeued() {
            // assert lock.isHeldByCurrentThread();
            if (count == 0)
                queueIsEmpty();
            else if (takeIndex == 0)
                takeIndexWrapped();
        }
    }

    private class Itr implements Iterator<E> {
        /**
         * Index to look for new nextItem; NONE at end
         */
        private int cursor;

        /**
         * Element to be returned by next call to next(); null if none
         */
        private E nextItem;

        /**
         * Index of nextItem; NONE if none, REMOVED if removed elsewhere
         */
        private int nextIndex;

        /**
         * Last element returned; null if none or not detached.
         */
        private E lastItem;

        /**
         * Index of lastItem, NONE if none, REMOVED if removed elsewhere
         */
        private int lastRet;

        /**
         * Previous value of takeIndex, or DETACHED when detached
         */
        private int prevTakeIndex;

        /**
         * Previous value of iters.cycles
         */
        private int prevCycles;

        /**
         * Special index value indicating "not available" or "undefined"
         */
        private static final int NONE = -1;

        /**
         * Special index value indicating "removed elsewhere", that is,
         * removed by some operation other than a call to this.remove().
         */
        private static final int REMOVED = -2;

        /**
         * Special value for prevTakeIndex indicating "detached mode"
         */
        private static final int DETACHED = -3;

        Itr() {
            lastRet = NONE;
            final ReentrantLock lock = ArrayBlockingQueue.this.lock;
            lock.lock();
            try {
                if (count == 0) {
                    // assert itrs == null;
                    cursor = NONE;
                    nextIndex = NONE;
                    prevTakeIndex = DETACHED;
                } else {
                    final int takeIndex = ArrayBlockingQueue.this.takeIndex;
                    prevTakeIndex = takeIndex;
                    nextItem = itemAt(nextIndex = takeIndex);
                    cursor = incCursor(takeIndex);
                    if (itrs == null) {
                        itrs = new Itrs(this);
                    } else {
                        itrs.register(this); // in this order
                        itrs.doSomeSweeping(false);
                    }
                    prevCycles = itrs.cycles;
                    // assert takeIndex >= 0;
                    // assert prevTakeIndex == takeIndex;
                    // assert nextIndex >= 0;
                    // assert nextItem != null;
                }
            } finally {
                lock.unlock();
            }
        }

        boolean isDetached() {
            // assert lock.isHeldByCurrentThread();
            return prevTakeIndex < 0;
        }

        private int incCursor(int index) {
            // assert lock.isHeldByCurrentThread();
            if (++index == items.length)
                index = 0;
            if (index == putIndex)
                index = NONE;
            return index;
        }

        /**
         * Returns true if index is invalidated by the given number of
         * dequeues, starting from prevTakeIndex.
         */
        private boolean invalidated(int index, int prevTakeIndex, long dequeues, int length) {
            if (index < 0)
                return false;
            int distance = index - prevTakeIndex;
            if (distance < 0)
                distance += length;
            return dequeues > distance;
        }

        /**
         * Adjusts indices to incorporate all dequeues since the last
         * operation on this iterator.  Call only from iterating thread.
         */
        private void incorporateDequeues() {
            // assert lock.isHeldByCurrentThread();
            // assert itrs != null;
            // assert !isDetached();
            // assert count > 0;

            final int cycles = itrs.cycles;
            final int takeIndex = ArrayBlockingQueue.this.takeIndex;
            final int prevCycles = this.prevCycles;
            final int prevTakeIndex = this.prevTakeIndex;

            if (cycles != prevCycles || takeIndex != prevTakeIndex) {
                final int len = items.length;
                // how far takeIndex has advanced since the previous
                // operation of this iterator
                long dequeues = (long)(cycles - prevCycles) * len + (takeIndex - prevTakeIndex);

                // Check indices for invalidation
                if (invalidated(lastRet, prevTakeIndex, dequeues, len))
                    lastRet = REMOVED;
                if (invalidated(nextIndex, prevTakeIndex, dequeues, len))
                    nextIndex = REMOVED;
                if (invalidated(cursor, prevTakeIndex, dequeues, len))
                    cursor = takeIndex;

                if (cursor < 0 && nextIndex < 0 && lastRet < 0)
                    detach();
                else {
                    this.prevCycles = cycles;
                    this.prevTakeIndex = takeIndex;
                }
            }
        }

        /**
         * Called when itrs should stop tracking this iterator, either
         * because there are no more indices to update (cursor < 0 &&
         * nextIndex < 0 && lastRet < 0) or as a special exception, when
         * lastRet >= 0, because hasNext() is about to return false for the
         * first time.  Call only from iterating thread.
         */
        private void detach() {
            // Switch to detached mode
            // assert lock.isHeldByCurrentThread();
            // assert cursor == NONE;
            // assert nextIndex < 0;
            // assert lastRet < 0 || nextItem == null;
            // assert lastRet < 0 ^ lastItem != null;
            if (prevTakeIndex >= 0) {
                // assert itrs != null;
                prevTakeIndex = DETACHED;
                // try to unlink from itrs (but not too hard)
                itrs.doSomeSweeping(true);
            }
        }

        /**
         * For performance reasons, we would like not to acquire a lock in
         * hasNext in the common case.  To allow for this, we only access
         * fields (i.e. nextItem) that are not modified by update operations
         * triggered by queue modifications.
         */
        public boolean hasNext() {
            if (nextItem != null)
                return true;
            noNext();
            return false;
        }

        private void noNext() {
            final ReentrantLock lock = ArrayBlockingQueue.this.lock;
            lock.lock();
            try {
                // assert cursor == NONE;
                // assert nextIndex == NONE;
                if (!isDetached()) {
                    // assert lastRet >= 0;
                    incorporateDequeues(); // might update lastRet
                    if (lastRet >= 0) {
                        lastItem = itemAt(lastRet);
                        // assert lastItem != null;
                        detach();
                    }
                }
                // assert isDetached();
                // assert lastRet < 0 ^ lastItem != null;
            } finally {
                lock.unlock();
            }
        }

        public E next() {
            final E e = nextItem;
            if (e == null)
                throw new NoSuchElementException();
            final ReentrantLock lock = ArrayBlockingQueue.this.lock;
            lock.lock();
            try {
                if (!isDetached())
                    incorporateDequeues();
                // assert nextIndex != NONE;
                // assert lastItem == null;
                lastRet = nextIndex;
                final int cursor = this.cursor;
                if (cursor >= 0) {
                    nextItem = itemAt(nextIndex = cursor);
                    // assert nextItem != null;
                    this.cursor = incCursor(cursor);
                } else {
                    nextIndex = NONE;
                    nextItem = null;
                    if (lastRet == REMOVED)
                        detach();
                }
            } finally {
                lock.unlock();
            }
            return e;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            final ReentrantLock lock = ArrayBlockingQueue.this.lock;
            lock.lock();
            try {
                final E e = nextItem;
                if (e == null)
                    return;
                if (!isDetached())
                    incorporateDequeues();
                action.accept(e);
                if (isDetached() || cursor < 0)
                    return;
                final Object[] items = ArrayBlockingQueue.this.items;
                for (int i = cursor, end = putIndex, to = (i < end) ? end : items.length; ; i = 0, to = end) {
                    for (; i < to; i++)
                        action.accept(itemAt(items, i));
                    if (to == end)
                        break;
                }
            } finally {
                // Calling forEachRemaining is a strong hint that this
                // iteration is surely over; supporting remove() after
                // forEachRemaining() is more trouble than it's worth
                cursor = nextIndex = lastRet = NONE;
                nextItem = lastItem = null;
                detach();
                lock.unlock();
            }
        }

        public void remove() {
            final ReentrantLock lock = ArrayBlockingQueue.this.lock;
            lock.lock();
            // assert lock.getHoldCount() == 1;
            try {
                if (!isDetached())
                    incorporateDequeues(); // might update lastRet or detach
                final int lastRet = this.lastRet;
                this.lastRet = NONE;
                if (lastRet >= 0) {
                    if (!isDetached())
                        removeAt(lastRet);
                    else {
                        final E lastItem = this.lastItem;
                        // assert lastItem != null;
                        this.lastItem = null;
                        if (itemAt(lastRet) == lastItem)
                            removeAt(lastRet);
                    }
                } else if (lastRet == NONE)
                    throw new IllegalStateException();
                // else lastRet == REMOVED and the last returned element was
                // previously asynchronously removed via an operation other
                // than this.remove(), so nothing to do.

                if (cursor < 0 && nextIndex < 0)
                    detach();
            } finally {
                lock.unlock();
                // assert lastRet == NONE;
                // assert lastItem == null;
            }
        }

        /**
         * Called to notify the iterator that the queue is empty, or that it
         * has fallen hopelessly behind, so that it should abandon any
         * further iteration, except possibly to return one more element
         * from next(), as promised by returning true from hasNext().
         */
        void shutdown() {
            // assert lock.isHeldByCurrentThread();
            cursor = NONE;
            if (nextIndex >= 0)
                nextIndex = REMOVED;
            if (lastRet >= 0) {
                lastRet = REMOVED;
                lastItem = null;
            }
            prevTakeIndex = DETACHED;
            // Don't set nextItem to null because we must continue to be
            // able to return it on next().
            //
            // Caller will unlink from itrs when convenient.
        }

        private int distance(int index, int prevTakeIndex, int length) {
            int distance = index - prevTakeIndex;
            if (distance < 0)
                distance += length;
            return distance;
        }

        /**
         * Called whenever an interior remove (not at takeIndex) occurred.
         *
         * @return true if this iterator should be unlinked from itrs
         */
        boolean removedAt(int removedIndex) {
            // assert lock.isHeldByCurrentThread();
            if (isDetached())
                return true;

            final int takeIndex = ArrayBlockingQueue.this.takeIndex;
            final int prevTakeIndex = this.prevTakeIndex;
            final int len = items.length;
            // distance from prevTakeIndex to removedIndex
            final int removedDistance = len * (itrs.cycles - this.prevCycles + ((removedIndex < takeIndex) ? 1 : 0)) + (removedIndex - prevTakeIndex);
            // assert itrs.cycles - this.prevCycles >= 0;
            // assert itrs.cycles - this.prevCycles <= 1;
            // assert removedDistance > 0;
            // assert removedIndex != takeIndex;
            int cursor = this.cursor;
            if (cursor >= 0) {
                int x = distance(cursor, prevTakeIndex, len);
                if (x == removedDistance) {
                    if (cursor == putIndex)
                        this.cursor = cursor = NONE;
                } else if (x > removedDistance) {
                    // assert cursor != prevTakeIndex;
                    this.cursor = cursor = dec(cursor, len);
                }
            }
            int lastRet = this.lastRet;
            if (lastRet >= 0) {
                int x = distance(lastRet, prevTakeIndex, len);
                if (x == removedDistance)
                    this.lastRet = lastRet = REMOVED;
                else if (x > removedDistance)
                    this.lastRet = lastRet = dec(lastRet, len);
            }
            int nextIndex = this.nextIndex;
            if (nextIndex >= 0) {
                int x = distance(nextIndex, prevTakeIndex, len);
                if (x == removedDistance)
                    this.nextIndex = nextIndex = REMOVED;
                else if (x > removedDistance)
                    this.nextIndex = nextIndex = dec(nextIndex, len);
            }
            if (cursor < 0 && nextIndex < 0 && lastRet < 0) {
                this.prevTakeIndex = DETACHED;
                return true;
            }
            return false;
        }

        /**
         * Called whenever takeIndex wraps around to zero.
         *
         * @return true if this iterator should be unlinked from itrs
         */
        boolean takeIndexWrapped() {
            // assert lock.isHeldByCurrentThread();
            if (isDetached())
                return true;
            if (itrs.cycles - prevCycles > 1) {
                // All the elements that existed at the time of the last
                // operation are gone, so abandon further iteration.
                shutdown();
                return true;
            }
            return false;
        }

        //         /** Uncomment for debugging. */
        //         public String toString() {
        //             return ("cursor=" + cursor + " " +
        //                     "nextIndex=" + nextIndex + " " +
        //                     "lastRet=" + lastRet + " " +
        //                     "nextItem=" + nextItem + " " +
        //                     "lastItem=" + lastItem + " " +
        //                     "prevCycles=" + prevCycles + " " +
        //                     "prevTakeIndex=" + prevTakeIndex + " " +
        //                     "size()=" + size() + " " +
        //                     "remainingCapacity()=" + remainingCapacity());
        //         }
    }

    public Spliterator<E> spliterator() {
        return Spliterators.spliterator(this, (Spliterator.ORDERED | Spliterator.NONNULL | Spliterator.CONCURRENT));
    }

    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (count > 0) {
                final Object[] items = this.items;
                for (int i = takeIndex, end = putIndex, to = (i < end) ? end : items.length; ; i = 0, to = end) {
                    for (; i < to; i++)
                        action.accept(itemAt(items, i));
                    if (to == end)
                        break;
                }
            }
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

    public boolean retainAll(Collection<?> c) {
        Objects.requireNonNull(c);
        return bulkRemove(e -> !c.contains(e));
    }

    private boolean bulkRemove(Predicate<? super E> filter) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (itrs == null) { // check for active iterators
                if (count > 0) {
                    final Object[] items = this.items;
                    // Optimize for initial run of survivors
                    for (int i = takeIndex, end = putIndex, to = (i < end) ? end : items.length; ; i = 0, to = end) {
                        for (; i < to; i++)
                            if (filter.test(itemAt(items, i)))
                                return bulkRemoveModified(filter, i);
                        if (to == end)
                            break;
                    }
                }
                return false;
            }
        } finally {
            lock.unlock();
        }
        // Active iterators are too hairy!
        // Punting (for now) to the slow n^2 algorithm ...
        return super.removeIf(filter);
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

    private int distanceNonEmpty(int i, int j) {
        if ((j -= i) <= 0)
            j += items.length;
        return j;
    }

    private boolean bulkRemoveModified(Predicate<? super E> filter, final int beg) {
        final Object[] es = items;
        final int capacity = items.length;
        final int end = putIndex;
        final long[] deathRow = nBits(distanceNonEmpty(beg, putIndex));
        deathRow[0] = 1L;   // set bit 0
        for (int i = beg + 1, to = (i <= end) ? end : es.length, k = beg; ; i = 0, to = end, k -= capacity) {
            for (; i < to; i++)
                if (filter.test(itemAt(es, i)))
                    setBit(deathRow, i - k);
            if (to == end)
                break;
        }
        // a two-finger traversal, with hare i reading, tortoise w writing
        int w = beg;
        for (int i = beg + 1, to = (i <= end) ? end : es.length, k = beg; ; w = 0) { // w rejoins i on second leg
            // In this loop, i and w are on the same leg, with i > w
            for (; i < to; i++)
                if (isClear(deathRow, i - k))
                    es[w++] = es[i];
            if (to == end)
                break;
            // In this loop, w is on the first leg, i on the second
            for (i = 0, to = end, k -= capacity; i < to && w < capacity; i++)
                if (isClear(deathRow, i - k))
                    es[w++] = es[i];
            if (i >= to) {
                if (w == capacity)
                    w = 0; // "corner" case
                break;
            }
        }
        count -= distanceNonEmpty(w, end);
        circularClear(es, putIndex = w, end);
        return true;
    }

    void checkInvariants() {
        // meta-assertions
        // assert lock.isHeldByCurrentThread();
        if (!invariantsSatisfied()) {
            String detail = String.format("takeIndex=%d putIndex=%d count=%d capacity=%d items=%s", takeIndex, putIndex, count, items.length,
                                          Arrays.toString(items));
            System.err.println(detail);
            throw new AssertionError(detail);
        }
    }

    private boolean invariantsSatisfied() {
        // Unlike ArrayDeque, we have a count field but no spare slot.
        // We prefer ArrayDeque's strategy (and the names of its fields!),
        // but our field layout is baked into the serial form, and so is
        // too annoying to change.
        //
        // putIndex == takeIndex must be disambiguated by checking count.
        int capacity = items.length;
        return capacity > 0 && items.getClass() == Object[].class && (takeIndex | putIndex | count) >= 0 && takeIndex < capacity &&
               putIndex < capacity && count <= capacity && (putIndex - takeIndex - count) % capacity == 0 &&
               (count == 0 || items[takeIndex] != null) && (count == capacity || items[putIndex] == null) && (count == 0 || items[dec(putIndex,
                                                                                                                                      capacity)] !=
                                                                                                                            null);
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {

        // Read in items array and various fields
        s.defaultReadObject();

        if (!invariantsSatisfied())
            throw new java.io.InvalidObjectException("invariants violated");
    }
}
