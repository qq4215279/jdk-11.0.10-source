/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SynchronousQueue是一种特殊的BlockingQueue，它本身没有容量。先调put(...)，线程会阻塞；直到另外一个线程调用了take()，两个线程才同时解锁，反之亦然。
 * 对于多个线程而言，例如3个线程，调用3次put(...)，3个线程都会阻塞；直到另外的线程调用3次take()，6个线程才同时解锁，反之亦然。
 *
 * @author liuzhen
 * @date 2022/4/15 23:37
 * @return
 */
public class SynchronousQueue<E> extends AbstractQueue<E> implements BlockingQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = -3223113410248163686L;

    static final int MAX_TIMED_SPINS = (Runtime.getRuntime().availableProcessors() < 2) ? 0 : 32;

    static final int MAX_UNTIMED_SPINS = MAX_TIMED_SPINS * 16;

    static final long SPIN_FOR_TIMEOUT_THRESHOLD = 1000L;

    /**
     * @author liuzhen
     * @date 2022/4/15 23:46
     * @return
     */
    abstract static class Transferer<E> {
        /**
         *
         * @author liuzhen
         * @date 2022/4/16 10:09
         * @param e     对应元素
         * @param timed 是否设置超时
         * @param nanos 对应的超时时间
         * @return E
         */
        abstract E transfer(E e, boolean timed, long nanos);
    }

    /**
     * 非公平模式（栈模式）
     * 也是一个单向链表。不同于队列，只需要head指针就能实现入栈和出栈操作。
     * @author liuzhen
     * @date 2022/4/16 10:04
     * @return
     */
    static final class TransferStack<E> extends Transferer<E> {

        /* Modes for SNodes, ORed together in node fields */
        static final int REQUEST = 0;
        static final int DATA = 1;
        static final int FULFILLING = 2;

        static boolean isFulfilling(int m) {
            return (m & FULFILLING) != 0;
        }

        /**
         * 链表中的节点有三种状态，REQUEST对应take节点，DATA对应put节点，二者配对之后，会生成一个FULFILLING节点，入栈，然后FULLING节点和被配对的节点一起出栈。
         * @author liuzhen
         * @date 2022/4/16 10:28
         * @return
         */
        static final class SNode {
            /** 单向链表 */
            volatile SNode next;        // next node in stack
            /** 配对的节点 */
            volatile SNode match;       // the node matched to this
            /** 对应的阻塞线程 */
            volatile Thread waiter;     // to control park/unpark
            Object item;                // data; or null for REQUESTs
            /** 三种模式 */
            int mode;

            SNode(Object item) {
                this.item = item;
            }

            boolean casNext(SNode cmp, SNode val) {
                return cmp == next && SNEXT.compareAndSet(this, cmp, val);
            }

            boolean tryMatch(SNode s) {
                if (match == null && SMATCH.compareAndSet(this, null, s)) {
                    Thread w = waiter;
                    if (w != null) {    // waiters need at most one unpark
                        waiter = null;
                        LockSupport.unpark(w);
                    }
                    return true;
                }
                return match == s;
            }

            void tryCancel() {
                SMATCH.compareAndSet(this, null, this);
            }

            boolean isCancelled() {
                return match == this;
            }

            // VarHandle mechanics
            private static final VarHandle SMATCH;
            private static final VarHandle SNEXT;

            static {
                try {
                    MethodHandles.Lookup l = MethodHandles.lookup();
                    SMATCH = l.findVarHandle(SNode.class, "match", SNode.class);
                    SNEXT = l.findVarHandle(SNode.class, "next", SNode.class);
                } catch (ReflectiveOperationException e) {
                    throw new ExceptionInInitializerError(e);
                }
            }
        }

        volatile SNode head;

        boolean casHead(SNode h, SNode nh) {
            return h == head && SHEAD.compareAndSet(this, h, nh);
        }

        static SNode snode(SNode s, Object e, SNode next, int mode) {
            if (s == null)
                s = new SNode(e);
            s.mode = mode;
            s.next = next;
            return s;
        }

        /**
         *
         * @param e
         * @param timed
         * @param nanos
         * @return
         */
        @SuppressWarnings("unchecked")
        E transfer(E e, boolean timed, long nanos) {

            SNode s = null; // constructed/reused as needed
            int mode = (e == null) ? REQUEST : DATA;

            for (; ; ) {
                SNode h = head;
                // 同一种模式
                if (h == null || h.mode == mode) {  // empty or same-mode
                    if (timed && nanos <= 0L) {     // can't wait
                        if (h != null && h.isCancelled())
                            casHead(h, h.next);     // pop cancelled node
                        else
                            return null;
                    } else if (casHead(h, s = snode(s, e, h, mode))) { // 入栈
                        // 阻塞等待
                        SNode m = awaitFulfill(s, timed, nanos);
                        if (m == s) {               // wait was cancelled
                            clean(s);
                            return null;
                        }
                        if ((h = head) != null && h.next == s)
                            casHead(h, s.next);     // help s's fulfiller
                        return (E)((mode == REQUEST) ? m.item : s.item);
                    }
                } else if (!isFulfilling(h.mode)) { // 非同一种模式，待匹配 // try to fulfill
                    if (h.isCancelled())            // already cancelled
                        casHead(h, h.next);         // pop and retry
                    else if (casHead(h, s = snode(s, e, h, FULFILLING | mode))) { // 生成一个FULFILLING节点，入栈
                        for (; ; ) { // loop until matched or waiters disappear
                            SNode m = s.next;       // m is s's match
                            if (m == null) {        // all waiters are gone
                                casHead(s, null);   // pop fulfill node
                                s = null;           // use new node next time
                                break;              // restart main loop
                            }
                            SNode mn = m.next;
                            if (m.tryMatch(s)) {
                                // 两个节点一起出栈
                                casHead(s, mn);     // pop both s and m
                                return (E)((mode == REQUEST) ? m.item : s.item);
                            } else                  // lost match
                                s.casNext(m, mn);   // help unlink
                        }
                    }
                } else { // 已经匹配过了，出栈       // help a fulfiller
                    SNode m = h.next;               // m is h's match
                    if (m == null)                  // waiter is gone
                        casHead(h, null);           // pop fulfilling node
                    else {
                        SNode mn = m.next;
                        // 配对，一起出栈
                        if (m.tryMatch(h))          // help match
                            casHead(h, mn);         // pop both h and m
                        else                        // lost match
                            h.casNext(m, mn);       // help unlink
                    }
                }
            }
        }

        SNode awaitFulfill(SNode s, boolean timed, long nanos) {
            final long deadline = timed ? System.nanoTime() + nanos : 0L;
            Thread w = Thread.currentThread();
            int spins = shouldSpin(s) ? (timed ? MAX_TIMED_SPINS : MAX_UNTIMED_SPINS) : 0;
            for (; ; ) {
                if (w.isInterrupted())
                    s.tryCancel();
                SNode m = s.match;
                if (m != null)
                    return m;
                if (timed) {
                    nanos = deadline - System.nanoTime();
                    if (nanos <= 0L) {
                        s.tryCancel();
                        continue;
                    }
                }
                if (spins > 0) {
                    Thread.onSpinWait();
                    spins = shouldSpin(s) ? (spins - 1) : 0;
                } else if (s.waiter == null)
                    s.waiter = w; // establish waiter so can park next iter
                else if (!timed)
                    LockSupport.park(this);
                else if (nanos > SPIN_FOR_TIMEOUT_THRESHOLD)
                    LockSupport.parkNanos(this, nanos);
            }
        }

        boolean shouldSpin(SNode s) {
            SNode h = head;
            return (h == s || h == null || isFulfilling(h.mode));
        }

        void clean(SNode s) {
            s.item = null;   // forget item
            s.waiter = null; // forget thread

            SNode past = s.next;
            if (past != null && past.isCancelled())
                past = past.next;

            // Absorb cancelled nodes at head
            SNode p;
            while ((p = head) != null && p != past && p.isCancelled())
                casHead(p, p.next);

            // Unsplice embedded nodes
            while (p != null && p != past) {
                SNode n = p.next;
                if (n != null && n.isCancelled())
                    p.casNext(n, n.next);
                else
                    p = n;
            }
        }

        // VarHandle mechanics
        private static final VarHandle SHEAD;

        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                SHEAD = l.findVarHandle(TransferStack.class, "head", SNode.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    /**
     * 公平模式（队列模式）
     * TransferQueue是一个基于单向链表而实现的队列，通过head和tail 2个指针记录头部和尾部。初始的时候，head和tail会指向一个空节点，
     * @author liuzhen
     * @date 2022/4/16 10:02
     * @return
     */
    static final class TransferQueue<E> extends Transferer<E> {

        static final class QNode {
            volatile QNode next;          // next node in queue
            volatile Object item;         // CAS'ed to or from null
            volatile Thread waiter;       // to control park/unpark
            final boolean isData;

            QNode(Object item, boolean isData) {
                this.item = item;
                this.isData = isData;
            }

            boolean casNext(QNode cmp, QNode val) {
                return next == cmp && QNEXT.compareAndSet(this, cmp, val);
            }

            boolean casItem(Object cmp, Object val) {
                return item == cmp && QITEM.compareAndSet(this, cmp, val);
            }

            void tryCancel(Object cmp) {
                QITEM.compareAndSet(this, cmp, this);
            }

            boolean isCancelled() {
                return item == this;
            }

            boolean isOffList() {
                return next == this;
            }

            // VarHandle mechanics
            private static final VarHandle QITEM;
            private static final VarHandle QNEXT;

            static {
                try {
                    MethodHandles.Lookup l = MethodHandles.lookup();
                    QITEM = l.findVarHandle(QNode.class, "item", Object.class);
                    QNEXT = l.findVarHandle(QNode.class, "next", QNode.class);
                } catch (ReflectiveOperationException e) {
                    throw new ExceptionInInitializerError(e);
                }
            }
        }

        // -------------------->

        transient volatile QNode head;
        transient volatile QNode tail;
        transient volatile QNode cleanMe;

        TransferQueue() {
            QNode h = new QNode(null, false); // initialize to dummy node.
            head = h;
            tail = h;
        }

        void advanceHead(QNode h, QNode nh) {
            if (h == head && QHEAD.compareAndSet(this, h, nh))
                h.next = h; // forget old next
        }

        void advanceTail(QNode t, QNode nt) {
            if (tail == t)
                QTAIL.compareAndSet(this, t, nt);
        }

        boolean casCleanMe(QNode cmp, QNode val) {
            return cleanMe == cmp && QCLEANME.compareAndSet(this, cmp, val);
        }

        /**
         *
         * 整个 for 循环有两个大的 if-else 分支，如果当前线程和队列中的元素是同一种模式（都是put节点或者take节点），则与当前线程对应的节点被加入队列尾部并且阻塞；
         * 如果不是同一种模式，则选取队列头部的第1个元素进行配对。
         * 这里的配对就是m.casItem（x，e），把自己的item x换成对方的item e，如果CAS操作成功，则配对成功。如果是put节点，则isData=true，item！=null；
         * 如果是take节点，则isData=false，item=null。如果CAS操作不成功，则isData和item之间将不一致，也就是isData！=（x！=null），通过这个条件可以判断节点是否已经被匹配过了。
         * @param e
         * @param timed
         * @param nanos
         * @return
         */
        @SuppressWarnings("unchecked")
        E transfer(E e, boolean timed, long nanos) {

            QNode s = null; // constructed/reused as needed
            boolean isData = (e != null);

            for (; ; ) {
                QNode t = tail;
                QNode h = head;
                // 队列未初始化，自旋等待
                if (t == null || h == null)         // saw uninitialized value
                    continue;                       // spin

                // 队列为空 或 当前线程和队列中元素未同一种模式
                if (h == t || t.isData == isData) { // empty or same-mode
                    QNode tn = t.next;
                    // 不一致读，重新执行for循环
                    if (t != tail)                  // inconsistent read
                        continue;
                    if (tn != null) {               // lagging tail
                        advanceTail(t, tn);
                        continue;
                    }
                    if (timed && nanos <= 0L)       // can't wait
                        return null;
                    if (s == null)
                        // 新建一个节点
                        s = new QNode(e, isData);
                    // 加入尾部
                    if (!t.casNext(null, s))        // failed to link in
                        continue;

                    // 后移tail指针
                    advanceTail(t, s);              // swing tail and wait
                    // 进入阻塞状态
                    Object x = awaitFulfill(s, e, timed, nanos);
                    if (x == s) {                   // wait was cancelled
                        clean(t, s);
                        return null;
                    }

                    // 从阻塞中唤醒，确定已经处于队列中的第一个元素
                    if (!s.isOffList()) {           // not already unlinked
                        advanceHead(t, s);          // unlink if head
                        if (x != null)              // and forget fields
                            s.item = s;
                        s.waiter = null;
                    }
                    return (x != null) ? (E)x : e;

                } else { // 当前线程可以和队列中的第一个元素进行匹配                           // complementary-mode
                    QNode m = h.next;               // node to fulfill
                    // 取队列中的第一个元素不一致读，重新执行for循环
                    if (t != tail || m == null || h != head)
                        continue;                   // inconsistent read

                    Object x = m.item;
                    // 已经匹配
                    if (isData == (x != null) ||    // m already fulfilled
                        x == m ||                   // m cancelled
                        // 尝试配对
                        !m.casItem(x, e)) {         // lost CAS
                        // 已经配对，直接出队列
                        advanceHead(h, m);          // dequeue and retry
                        continue;
                    }

                    // 配对成功，出队列
                    advanceHead(h, m);              // successfully fulfilled
                    // 唤醒队列中与第一个元素对应的线程
                    LockSupport.unpark(m.waiter);
                    return (x != null) ? (E)x : e;
                }
            }
        }

        Object awaitFulfill(QNode s, E e, boolean timed, long nanos) {
            /* Same idea as TransferStack.awaitFulfill */
            final long deadline = timed ? System.nanoTime() + nanos : 0L;
            Thread w = Thread.currentThread();
            int spins = (head.next == s) ? (timed ? MAX_TIMED_SPINS : MAX_UNTIMED_SPINS) : 0;
            for (; ; ) {
                if (w.isInterrupted())
                    s.tryCancel(e);
                Object x = s.item;
                if (x != e)
                    return x;
                if (timed) {
                    nanos = deadline - System.nanoTime();
                    if (nanos <= 0L) {
                        s.tryCancel(e);
                        continue;
                    }
                }
                if (spins > 0) {
                    --spins;
                    Thread.onSpinWait();
                } else if (s.waiter == null)
                    s.waiter = w;
                else if (!timed)
                    LockSupport.park(this);
                else if (nanos > SPIN_FOR_TIMEOUT_THRESHOLD)
                    LockSupport.parkNanos(this, nanos);
            }
        }

        void clean(QNode pred, QNode s) {
            s.waiter = null; // forget thread
            while (pred.next == s) { // Return early if already unlinked
                QNode h = head;
                QNode hn = h.next;   // Absorb cancelled first node as head
                if (hn != null && hn.isCancelled()) {
                    advanceHead(h, hn);
                    continue;
                }
                QNode t = tail;      // Ensure consistent read for tail
                if (t == h)
                    return;
                QNode tn = t.next;
                if (t != tail)
                    continue;
                if (tn != null) {
                    advanceTail(t, tn);
                    continue;
                }
                if (s != t) {        // If not tail, try to unsplice
                    QNode sn = s.next;
                    if (sn == s || pred.casNext(s, sn))
                        return;
                }
                QNode dp = cleanMe;
                if (dp != null) {    // Try unlinking previous cancelled node
                    QNode d = dp.next;
                    QNode dn;
                    if (d == null ||               // d is gone or
                        d == dp ||                 // d is off list or
                        !d.isCancelled() ||        // d not cancelled or
                        (d != t &&                 // d not tail and
                         (dn = d.next) != null &&  //   has successor
                         dn != d &&                //   that is on list
                         dp.casNext(d, dn)))       // d unspliced
                        casCleanMe(dp, null);
                    if (dp == pred)
                        return;      // s is already saved node
                } else if (casCleanMe(null, pred))
                    return;          // Postpone cleaning s
            }
        }

        // VarHandle mechanics
        private static final VarHandle QHEAD;
        private static final VarHandle QTAIL;
        private static final VarHandle QCLEANME;

        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                QHEAD = l.findVarHandle(TransferQueue.class, "head", QNode.class);
                QTAIL = l.findVarHandle(TransferQueue.class, "tail", QNode.class);
                QCLEANME = l.findVarHandle(TransferQueue.class, "cleanMe", QNode.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    private transient volatile Transferer<E> transferer;

    public SynchronousQueue() {
        this(false);
    }

    /**
     * @param fair
     * @return
     * @author liuzhen
     * @date 2022/4/15 23:41
     */
    public SynchronousQueue(boolean fair) {
        // 和锁一样，也有公平和非公平模式。如果是公平模式，则用TransferQueue实现；如果是非公平模式，则用TransferStack实现。
        transferer = fair ? new TransferQueue<E>() : new TransferStack<E>();
    }

    /**
     * @param e
     * @return void
     * @author liuzhen
     * @date 2022/4/15 23:45
     */
    public void put(E e) throws InterruptedException {
        if (e == null)
            throw new NullPointerException();
        if (transferer.transfer(e, false, 0) == null) {
            Thread.interrupted();
            throw new InterruptedException();
        }
    }

    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        if (e == null)
            throw new NullPointerException();
        if (transferer.transfer(e, true, unit.toNanos(timeout)) != null)
            return true;
        if (!Thread.interrupted())
            return false;
        throw new InterruptedException();
    }

    public boolean offer(E e) {
        if (e == null)
            throw new NullPointerException();
        return transferer.transfer(e, true, 0) != null;
    }

    /**
     * @param
     * @return E
     * @author liuzhen
     * @date 2022/4/15 23:44
     */
    public E take() throws InterruptedException {
        E e = transferer.transfer(null, false, 0);
        if (e != null)
            return e;
        Thread.interrupted();
        throw new InterruptedException();
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E e = transferer.transfer(null, true, unit.toNanos(timeout));
        if (e != null || !Thread.interrupted())
            return e;
        throw new InterruptedException();
    }

    public E poll() {
        return transferer.transfer(null, true, 0);
    }

    public boolean isEmpty() {
        return true;
    }

    public int size() {
        return 0;
    }

    public int remainingCapacity() {
        return 0;
    }

    public void clear() {
    }

    public boolean contains(Object o) {
        return false;
    }

    public boolean remove(Object o) {
        return false;
    }

    public boolean containsAll(Collection<?> c) {
        return c.isEmpty();
    }

    public boolean removeAll(Collection<?> c) {
        return false;
    }

    public boolean retainAll(Collection<?> c) {
        return false;
    }

    public E peek() {
        return null;
    }

    public Iterator<E> iterator() {
        return Collections.emptyIterator();
    }

    public Spliterator<E> spliterator() {
        return Spliterators.emptySpliterator();
    }

    public Object[] toArray() {
        return new Object[0];
    }

    public <T> T[] toArray(T[] a) {
        if (a.length > 0)
            a[0] = null;
        return a;
    }

    public String toString() {
        return "[]";
    }

    public int drainTo(Collection<? super E> c) {
        Objects.requireNonNull(c);
        if (c == this)
            throw new IllegalArgumentException();
        int n = 0;
        for (E e; (e = poll()) != null; n++)
            c.add(e);
        return n;
    }

    public int drainTo(Collection<? super E> c, int maxElements) {
        Objects.requireNonNull(c);
        if (c == this)
            throw new IllegalArgumentException();
        int n = 0;
        for (E e; n < maxElements && (e = poll()) != null; n++)
            c.add(e);
        return n;
    }

    static class WaitQueue implements java.io.Serializable {
    }

    static class LifoWaitQueue extends WaitQueue {
        private static final long serialVersionUID = -3633113410248163686L;
    }

    static class FifoWaitQueue extends WaitQueue {
        private static final long serialVersionUID = -3623113410248163686L;
    }

    private ReentrantLock qlock;
    private WaitQueue waitingProducers;
    private WaitQueue waitingConsumers;

    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        boolean fair = transferer instanceof TransferQueue;
        if (fair) {
            qlock = new ReentrantLock(true);
            waitingProducers = new FifoWaitQueue();
            waitingConsumers = new FifoWaitQueue();
        } else {
            qlock = new ReentrantLock();
            waitingProducers = new LifoWaitQueue();
            waitingConsumers = new LifoWaitQueue();
        }
        s.defaultWriteObject();
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        if (waitingProducers instanceof FifoWaitQueue)
            transferer = new TransferQueue<E>();
        else
            transferer = new TransferStack<E>();
    }

    static {
        Class<?> ensureLoaded = LockSupport.class;
    }
}
