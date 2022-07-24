/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent.locks;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * AQS：
 * 阻塞队列是整个AQS核心中的核心。如下图所示，head指向双向链表头部，tail指向双向链表尾部。
 * 入队就是把新的Node加到tail后面，然后对tail进行CAS操作；出队就是对head进行CAS操作，把head向后移一个位置。
 * 初始的时候，head=tail=NULL；然后，在往队列中加入阻塞的线程时，会新建一个空的Node，让head和tail都指向这个空Node；
 * 之后，在后面加入被阻塞的线程对象。所以，当head=tail的时候，说明队列为空。
 *
 * 锁实现的基本原理
 * Sync的父类AbstractQueuedSynchronizer经常被称作队列同步器（AQS），这个类非常重要，该类的父类是AbstractOwnableSynchronizer。
 * 此处的锁具备synchronized功能，即可以阻塞一个线程。为了实现一把具有阻塞或唤醒功能的锁，需要几个核心要素：
 * 1. 需要一个state变量，标记该锁的状态。state变量至少有两个值：0、1。对state变量的操作，使用CAS保证线程安全。
 * 2. 需要记录当前是哪个线程持有锁。
 * 3. 需要底层支持对一个线程进行阻塞或唤醒操作。
 * 4. 需要有一个队列维护所有阻塞的线程。这个队列也必须是线程安全的无锁队列，也需要使用CAS。
 * @author liuzhen
 * @date 2022/4/16 17:43
 * @return
 */
public abstract class AbstractQueuedSynchronizer extends AbstractOwnableSynchronizer implements java.io.Serializable {
    private static final long serialVersionUID = 7373984972572414691L;
    // Queuing utilities
    static final long SPIN_FOR_TIMEOUT_THRESHOLD = 1000L;

    /** 头节点 */
    private transient volatile Node head;
    /** 未节点 */
    private transient volatile Node tail;
    /**
     * 记录锁的状态，通过CAS修改state的值。
     * state取值不仅可以是0、1，还可以大于1，就是为了支持锁的可重入性。
     * 例如，同样一个线程，调用5次lock，state会变成5；然后调用5次unlock，state减为0。
     * 当state=0时，没有线程持有锁，exclusiveOwnerThread=null；
     * 当state=1时，有一个线程持有锁，exclusiveOwnerThread=该线程；
     * 当state > 1时，说明该线程重入了该锁。
     */
    private volatile int state;

    protected AbstractQueuedSynchronizer() {
    }

    /**
     * AQS中利用双向链表和CAS实现了一个阻塞队列（CLH队列的节点，代表“等待锁的线程队列”，每个Node都会一个线程对应）
     * 说明：
     * Node是CLH队列的节点，代表“等待锁的线程队列”。
     * 1. 每个Node都会一个线程对应。
     * 2. 每个Node会通过prev和next分别指向上一个节点和下一个节点，这分别代表上一个等待线程和下一个等待线程。
     * 3. Node通过waitStatus保存线程的等待状态。
     * 4. Node通过nextWaiter来区分线程是“独占锁”线程还是“共享锁”线程。如果是“独占锁”线程，则nextWaiter的值为EXCLUSIVE；如果是“共享锁”线程，则nextWaiter的值是SHARED。
     */
    static final class Node {
        static final Node SHARED = new Node();
        static final Node EXCLUSIVE = null;

        /** 线程已被取消，对应的waitStatus的值 */
        static final int CANCELLED = 1;
        /** “当前线程的后继线程需要被unpark(唤醒)”，对应的waitStatus的值。
         一般发生情况是：当前线程的后继线程处于阻塞状态，而当前线程被release或cancel掉，因此需要唤醒当前线程的后继线程。*/
        static final int SIGNAL = -1;
        /** 线程(处在Condition休眠状态)在等待Condition唤醒，对应的waitStatus的值 */
        static final int CONDITION = -2;
        /** (共享锁)其它线程获取到“共享锁”，对应的waitStatus的值 */
        static final int PROPAGATE = -3;

        /** waitStatus为“CANCELLED, SIGNAL, CONDITION, PROPAGATE”时分别表示不同状态，
         若waitStatus=0，则意味着当前线程不属于上面的任何一种状态。*/
        volatile int waitStatus;

        /** 前一节点 */
        volatile Node prev;
        /** 后一节点 */
        volatile Node next;

        /** 每个Node对应一个被阻塞的线程 */
        volatile Thread thread;

        /** nextWaiter是“区别当前CLH队列是 ‘独占锁’队列 还是 ‘共享锁’队列 的标记”
           若nextWaiter=SHARED，则CLH队列是“共享锁”队列；
           若nextWaiter=EXCLUSIVE，(即nextWaiter=null)，则CLH队列是“独占锁”队列。 */
        Node nextWaiter;

        /**
         * “共享锁”则返回true，“独占锁”则返回false。
         * @date 2022/7/13 21:33
         * @param
         * @return boolean
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * 返回前一节点
         * @date 2022/7/13 21:33
         * @param
         * @return java.util.concurrent.locks.AbstractQueuedSynchronizer.Node
         */
        final Node predecessor() {
            Node p = prev;
            if (p == null)
                throw new NullPointerException();
            else
                return p;
        }

        Node() {
        }

        /**
         * 构造函数。thread是节点所对应的线程，mode是用来表示thread的锁是“独占锁”还是“共享锁”。
         * @date 2022/7/13 21:32
         * @param nextWaiter
         * @return
         */
        Node(Node nextWaiter) {
            this.nextWaiter = nextWaiter;
            THREAD.set(this, Thread.currentThread());
        }

        /**
         * 构造函数。thread是节点所对应的线程，waitStatus是线程的等待状态。
         * @date 2022/7/13 21:32
         * @param waitStatus
         * @return
         */
        Node(int waitStatus) {
            WAITSTATUS.set(this, waitStatus);
            THREAD.set(this, Thread.currentThread());
        }

        final boolean compareAndSetWaitStatus(int expect, int update) {
            return WAITSTATUS.compareAndSet(this, expect, update);
        }

        final boolean compareAndSetNext(Node expect, Node update) {
            return NEXT.compareAndSet(this, expect, update);
        }

        final void setPrevRelaxed(Node p) {
            PREV.set(this, p);
        }

        // VarHandle mechanics
        private static final VarHandle NEXT;
        private static final VarHandle PREV;
        private static final VarHandle THREAD;
        private static final VarHandle WAITSTATUS;

        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                NEXT = l.findVarHandle(Node.class, "next", Node.class);
                PREV = l.findVarHandle(Node.class, "prev", Node.class);
                THREAD = l.findVarHandle(Node.class, "thread", Thread.class);
                WAITSTATUS = l.findVarHandle(Node.class, "waitStatus", int.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    /**
     * Condition 实现类
     * Condition 的所有实现，都在 ConditionObject 类中
     * @date 2022/6/19 19:49
     * @return
     */
    public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;

        private transient Node firstWaiter;
        private transient Node lastWaiter;

        private static final int REINTERRUPT = 1;
        private static final int THROW_IE = -1;

        public ConditionObject() {
        }

        /**
         * 注意：signal()只是将线程从Condition的条件阻塞队列移动到了AQS的阻塞队列，此时线程并没有被唤醒，只有当持有锁的线程调用了await()方法或者unlock()方法后，
         * 才会唤醒AQS阻塞队列中被阻塞的线程。而如果不调用signal()，则阻塞在条件队列上的线程永远不可能有机会进入到AQS阻塞队列。
         * @date 2022/6/17 21:18
         * @param
         * @return void
         */
        public final void signal() {
            // 判断当前线程是否获取了锁，如果无锁，则抛异常
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            // 得到条件阻塞队列的队头
            Node first = firstWaiter;
            if (first != null)
                // 唤醒队头第一个，不同于AQS的阻塞队列 -- 发起通知
                doSignal(first);
            // 从条件阻塞队列唤醒后，还需要加入到AQS队列阻塞中
        }

        public final void signalAll() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            if (first != null)
                doSignalAll(first);
        }

        /**
         * 将队头节点移入到AQS阻塞队列中，并将该节点从条件队列中移除 -- 唤醒队列中的第1个线程
         * @date 2022/6/17 21:19
         * @param first
         * @return void
         */
        private void doSignal(Node first) {
            do {
                if ((firstWaiter = first.nextWaiter) == null)
                    lastWaiter = null;
                first.nextWaiter = null;
                // transferForSignal()将头节点移动到AQS阻塞队列
            } while (!transferForSignal(first) && (first = firstWaiter) != null);
        }

        private void doSignalAll(Node first) {
            lastWaiter = firstWaiter = null;
            do {
                Node next = first.nextWaiter;
                first.nextWaiter = null;
                transferForSignal(first);
                first = next;
            } while (first != null);
        }

        public final void awaitUninterruptibly() {
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean interrupted = false;
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if (Thread.interrupted())
                    interrupted = true;
            }
            if (acquireQueued(node, savedState) || interrupted)
                selfInterrupt();
        }

        /**
         * 关于await，有几个关键点要说明：
         * 1. 线程调用 await()的时候，肯定已经先拿到了锁。所以，在 addConditionWaiter()内部，对这个双向链表的操作不需要执行CAS操作，线程天生是安全的
         * 2. 在线程执行wait操作之前，必须先释放锁。也就是fullyRelease(node)，否则会发生死锁。这个和wait/notify与synchronized的配合机制一样。
         * 3. 线程从wait中被唤醒后，必须用acquireQueued(node, savedState)方法重新拿锁。
         * 4. checkInterruptWhileWaiting(node)代码在park(this)代码之后，是为了检测在park期间是否收到过中断信号。
         *  当线程从park中醒来时，有两种可能：一种是其他线程调用了unpark，另一种是收到中断信号。这里的await()方法是可以响应中断的，
         *  所以当发现自己是被中断唤醒的，而不是被unpark唤醒的时，会直接退出while循环，await()方法也会返回。
         * 5. isOnSyncQueue(node)用于判断该Node是否在AQS的同步队列里面。初始的时候，Node只 在Condition的队列里，而不在AQS的队列里。
         *  但执行notity操作的时候，会放进AQS的同步队列。
         * @date 2022/6/17 21:13
         * @param
         * @return void
         */
        public final void await() throws InterruptedException {
            // 正要执行await，被中断，抛出异常
            if (Thread.interrupted())
                throw new InterruptedException();
            // 加入Condition的等待队列中，此时只是Node加入到了队列,线程还未阻塞
            Node node = addConditionWaiter();
            // 阻塞在Condition之前必须先释放锁，否则会死锁 --释放锁
            int savedState = fullyRelease(node);
            int interruptMode = 0;
            /*
            * 检测节点是否在AQS阻塞队列中，如果不在，则唤醒自己也无法争抢锁，继续睡眠
              此处为假设两个线程，A持有锁，A调用await(),B直接被阻塞，然后A调用await(),让出锁，
              同时node插入到条件等待队列，且自己不再同步队列中，所以睡眠
              此时会唤醒B，B获得锁，然后B调用signal(),将A的Node从条件阻塞队列加入到同步队列，
              然后调用await()，B进入原来A的状态。同时在fullyRelease()函数中唤醒阻塞的第一个线程
              A醒来后，进行循环判断，判断自己在AQS中，
            * */
            while (!isOnSyncQueue(node)) {
                // 自己阻塞自己 -- 阻塞当前线程
                LockSupport.park(this);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            // 被signal()方法唤醒后，要重新尝试获取锁
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null) // clean up if cancelled
                // 清理条件队列中所有状态不是CONDITION状态的节点
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                // 被中断唤醒，抛中断异常
                reportInterruptAfterWait(interruptMode);
        }

        public final boolean await(long time, TimeUnit unit) throws InterruptedException {
            long nanosTimeout = unit.toNanos(time);
            if (Thread.interrupted())
                throw new InterruptedException();
            // We don't check for nanosTimeout <= 0L here, to allow
            // await(0, unit) as a way to "yield the lock".
            final long deadline = System.nanoTime() + nanosTimeout;
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout > SPIN_FOR_TIMEOUT_THRESHOLD)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        public final long awaitNanos(long nanosTimeout) throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            // We don't check for nanosTimeout <= 0L here, to allow
            // awaitNanos(0) as a way to "yield the lock".
            final long deadline = System.nanoTime() + nanosTimeout;
            long initialNanos = nanosTimeout;
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout > SPIN_FOR_TIMEOUT_THRESHOLD)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            long remaining = deadline - System.nanoTime(); // avoid overflow
            return (remaining <= initialNanos) ? remaining : Long.MIN_VALUE;
        }

        public final boolean awaitUntil(Date deadline) throws InterruptedException {
            long abstime = deadline.getTime();
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (System.currentTimeMillis() >= abstime) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                LockSupport.parkUntil(this, abstime);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        /**
         * 总体逻辑就是将Node加入到条件等待队列的队尾
         * @date 2022/6/17 21:15
         * @param
         * @return java.util.concurrent.locks.AbstractQueuedSynchronizer.Node
         */
        private Node addConditionWaiter() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node t = lastWaiter;
            // If lastWaiter is cancelled, clean out.
            if (t != null && t.waitStatus != Node.CONDITION) {
                unlinkCancelledWaiters();
                t = lastWaiter;
            }

            // 创建一个新的Node
            Node node = new Node(Node.CONDITION);
            // 如果队列为空，即队列为空时，将node插入到队列中，并且作为队列第一个节点
            if (t == null)
                firstWaiter = node;
            else
                t.nextWaiter = node;
            lastWaiter = node;
            return node;
        }

        private void unlinkCancelledWaiters() {
            Node t = firstWaiter;
            Node trail = null;
            while (t != null) {
                Node next = t.nextWaiter;
                if (t.waitStatus != Node.CONDITION) {
                    t.nextWaiter = null;
                    if (trail == null)
                        firstWaiter = next;
                    else
                        trail.nextWaiter = next;
                    if (next == null)
                        lastWaiter = trail;
                } else
                    trail = t;
                t = next;
            }
        }

        private int checkInterruptWhileWaiting(Node node) {
            return Thread.interrupted() ? (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) : 0;
        }

        private void reportInterruptAfterWait(int interruptMode) throws InterruptedException {
            if (interruptMode == THROW_IE)
                throw new InterruptedException();
            else if (interruptMode == REINTERRUPT)
                selfInterrupt();
        }

        //  support for instrumentation

        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        protected final boolean hasWaiters() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    return true;
            }
            return false;
        }

        protected final int getWaitQueueLength() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            int n = 0;
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    ++n;
            }
            return n;
        }

        protected final Collection<Thread> getWaitingThreads() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            ArrayList<Thread> list = new ArrayList<>();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    Thread t = w.thread;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }
    }

    // ================================================================>

    // Main exported methods

    /**
     * 尝试获取公平锁
     * @date 2022/7/13 22:01
     * @param arg
     * @return boolean
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试获取共享锁
     * @date 2022/7/13 22:01
     * @param arg
     * @return int
     */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     *
     * @date 2022/7/24 0:09
     * @param arg
     * @param nanosTimeout
     * @return boolean
     */
    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquireShared(arg) >= 0 || doAcquireSharedNanos(arg, nanosTimeout);
    }

    public final boolean tryAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquire(arg) || doAcquireNanos(arg, nanosTimeout);
    }

    /**
     * 尝试释放锁
     * @date 2022/7/13 22:01
     * @param arg
     * @return boolean
     */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试释放共享锁
     * @date 2022/7/13 22:02
     * @param arg
     * @return boolean
     */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取锁
     * 创建节点，尝试将节点追加到队列尾部。获取tail节点，将tail节点的next设置为当前节点。如果tail不存在，就初始化队列。
     * @author liuzhen
     * @date 2022/4/17 17:37
     * @param arg
     * @return void
     */
    public final void acquire(int arg) {
        // addWaiter(...)方法，就是为当前线程生成一个Node，然后把Node放入双向链表的尾部。
        // 要注意的是，这只是把Thread对象放入了一个队列中而已，线程本身并未阻塞。
        if (!tryAcquire(arg) && acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }

    public final void acquireInterruptibly(int arg) throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (!tryAcquire(arg))
            doAcquireInterruptibly(arg);
    }

    /**
     *
     * @date 2022/6/19 19:04
     * @param arg
     * @return void
     */
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0)
            doAcquireShared(arg);
    }

    /**
     * 从tryAcquireShared(...)方法的实现来看，只要state != 0，调用await()方法的线程便会被放入AQS的阻塞队列，进入阻塞状态。
     *
     * @param arg
     * @return void
     * @author liuzhen
     * @date 2022/4/16 17:50
     */
    public final void acquireSharedInterruptibly(int arg) throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        // 被CountDownLatch.Sync实现
        if (tryAcquireShared(arg) < 0)
            doAcquireSharedInterruptibly(arg);
    }

    /**
     * 释放锁
     * @date 2022/6/17 21:16
     * @param arg
     * @return boolean
     */
    public final boolean release(int arg) {
        if (tryRelease(arg)) {
            Node h = head;
            if (h != null && h.waitStatus != 0)
                // 唤醒阻塞队列上的第一个
                unparkSuccessor(h);
            return true;
        }
        return false;
    }

    /**
     *  AQS的模板方法
     * @author liuzhen
     * @date 2022/4/16 17:52
     * @param arg
     * @return boolean
     */
    public final boolean releaseShared(int arg) {
        // 由CountDownLatch.Sync实现
        if (tryReleaseShared(arg)) {
            doReleaseShared();
            return true;
        }
        return false;
    }

    private void doReleaseShared() {
        for (; ; ) {
            Node h = head;
            if (h != null && h != tail) {
                int ws = h.waitStatus;
                if (ws == Node.SIGNAL) {
                    if (!h.compareAndSetWaitStatus(Node.SIGNAL, 0))
                        continue;            // loop to recheck cases
                    unparkSuccessor(h);
                } else if (ws == 0 && !h.compareAndSetWaitStatus(0, Node.PROPAGATE))
                    continue;                // loop on failed CAS
            }
            if (h == head)                   // loop if head changed
                break;
        }
    }

    private void doAcquireSharedInterruptibly(int arg) throws InterruptedException {
        final Node node = addWaiter(Node.SHARED);
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt())
                    throw new InterruptedException();
            }
        } catch (Throwable t) {
            cancelAcquire(node);
            throw t;
        }
    }

    /**
     * addWaiter(...)方法，就是为当前线程生成一个Node，然后把Node放入双向链表的尾部。要注意的是，这只是把Thread对象放入了一个队列中而已，线程本身并未阻塞。
     * @param mode
     * @return
     */
    private Node addWaiter(Node mode) {
        Node node = new Node(mode);

        for (; ; ) {
            Node oldTail = tail;
            if (oldTail != null) {
                node.setPrevRelaxed(oldTail);
                if (compareAndSetTail(oldTail, node)) {
                    oldTail.next = node;
                    return node;
                }
            } else {
                initializeSyncQueue();
            }
        }
    }

    // ================================================================>

    // Queue inspection methods

    /**
     * 进入acquireQueued(...)，该线程被阻塞。在该方法返回的一刻，就是拿到锁的那一刻，也就是被唤醒的那一刻，此时会删除队列的第一个元素（head指针前移1个节点）。
     *
     * 首先，acquireQueued(...)方法有一个返回值，表示什么意思呢？
     * 虽然该方法不会中断响应，但它会记录被阻塞期间有没有其他线程向它发送过中断信号。如果有，则该方法会返回true；否则，返回false。
     *
     * 当 acquireQueued(...)返回 true 时，会调用 selfInterrupt()，自己给自己发送中断信号，也就是自己把自己的中断标志位设为true。
     * 之所以要这么做，是因为自己在阻塞期间，收到其他线程中断信号没有及时响应，现在要进行补偿。这样一来，如果该线程在lock代码块内部有调用sleep()
     * 之类的阻塞方法，就可以抛出异常，响应该中断信号。
     * @date 2022/7/14 20:11
     * @param node
     * @param arg
     * @return boolean
     */
    final boolean acquireQueued(final Node node, int arg) {
        boolean interrupted = false;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    return interrupted;
                }

                if (shouldParkAfterFailedAcquire(p, node))
                    interrupted |= parkAndCheckInterrupt();
            }
        } catch (Throwable t) {
            cancelAcquire(node);
            if (interrupted)
                selfInterrupt();
            throw t;
        }
    }

    /**
     * 线程调用 park()方法，自己把自己阻塞起来，直到被其他线程唤醒，该方法返回。
     * park()方法返回有两种情况：
     *  1. 其他线程调用了unpark(Thread t)。
     *  2. 其他线程调用了t.interrupt()。这里要注意的是，lock()不能响应中断，但LockSupport.park()会响应中断。
     *
     * 也正因为LockSupport.park()可能被中断唤醒，acquireQueued(...)方法才写了一个for死循环。唤醒之后，如果发现自己排在队列头部，就去拿锁；
     * 如果拿不到锁，则再次自己阻塞自己。不断重复此过程，直到拿到锁。
     * 被唤醒之后，通过Thread.interrupted()来判断是否被中断唤醒。如果是情况1，会返回false；如果是情况2，则返回true。
     * @date 2022/7/14 20:17
     * @param
     * @return boolean
     */
    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);
        return Thread.interrupted();
    }

    private void doAcquireInterruptibly(int arg) throws InterruptedException {
        final Node node = addWaiter(Node.EXCLUSIVE);
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    return;
                }
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt())
                    throw new InterruptedException();
            }
        } catch (Throwable t) {
            cancelAcquire(node);
            throw t;
        }
    }

    private boolean doAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.EXCLUSIVE);
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    return true;
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L) {
                    cancelAcquire(node);
                    return false;
                }
                if (shouldParkAfterFailedAcquire(p, node) && nanosTimeout > SPIN_FOR_TIMEOUT_THRESHOLD)
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } catch (Throwable t) {
            cancelAcquire(node);
            throw t;
        }
    }

    private void doAcquireShared(int arg) {
        final Node node = addWaiter(Node.SHARED);
        boolean interrupted = false;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node))
                    interrupted |= parkAndCheckInterrupt();
            }
        } catch (Throwable t) {
            cancelAcquire(node);
            throw t;
        } finally {
            if (interrupted)
                selfInterrupt();
        }
    }

    private boolean doAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.SHARED);
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        return true;
                    }
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L) {
                    cancelAcquire(node);
                    return false;
                }
                if (shouldParkAfterFailedAcquire(p, node) && nanosTimeout > SPIN_FOR_TIMEOUT_THRESHOLD)
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } catch (Throwable t) {
            cancelAcquire(node);
            throw t;
        }
    }

    protected final int getState() {
        return state;
    }

    protected final void setState(int newState) {
        state = newState;
    }

    protected final boolean compareAndSetState(int expect, int update) {
        return STATE.compareAndSet(this, expect, update);
    }

    private Node enq(Node node) {
        for (; ; ) {
            Node oldTail = tail;
            if (oldTail != null) {
                node.setPrevRelaxed(oldTail);
                if (compareAndSetTail(oldTail, node)) {
                    oldTail.next = node;
                    return oldTail;
                }
            } else {
                initializeSyncQueue();
            }
        }
    }

    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    private void unparkSuccessor(Node node) {
        int ws = node.waitStatus;
        if (ws < 0)
            node.compareAndSetWaitStatus(ws, 0);

        Node s = node.next;
        if (s == null || s.waitStatus > 0) {
            s = null;
            for (Node p = tail; p != node && p != null; p = p.prev)
                if (p.waitStatus <= 0)
                    s = p;
        }
        if (s != null)
            LockSupport.unpark(s.thread);
    }

    private void setHeadAndPropagate(Node node, int propagate) {
        Node h = head; // Record old head for check below
        setHead(node);

        if (propagate > 0 || h == null || h.waitStatus < 0 || (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s == null || s.isShared())
                doReleaseShared();
        }
    }

    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    // Utilities for various versions of acquire

    private void cancelAcquire(Node node) {
        // Ignore if node doesn't exist
        if (node == null)
            return;

        node.thread = null;

        // Skip cancelled predecessors
        Node pred = node.prev;
        while (pred.waitStatus > 0)
            node.prev = pred = pred.prev;

        Node predNext = pred.next;

        node.waitStatus = Node.CANCELLED;

        // If we are the tail, remove ourselves.
        if (node == tail && compareAndSetTail(node, pred)) {
            pred.compareAndSetNext(predNext, null);
        } else {
            // If successor needs signal, try to set pred's next-link
            // so it will get one. Otherwise wake it up to propagate.
            int ws;
            if (pred != head && ((ws = pred.waitStatus) == Node.SIGNAL || (ws <= 0 && pred.compareAndSetWaitStatus(ws, Node.SIGNAL))) &&
                pred.thread != null) {
                Node next = node.next;
                if (next != null && next.waitStatus <= 0)
                    pred.compareAndSetNext(predNext, next);
            } else {
                unparkSuccessor(node);
            }

            node.next = node; // help GC
        }
    }

    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;
        if (ws == Node.SIGNAL)
            return true;
        if (ws > 0) {
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
            pred.compareAndSetWaitStatus(ws, Node.SIGNAL);
        }
        return false;
    }

    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    public final boolean hasQueuedThreads() {
        for (Node p = tail, h = head; p != h && p != null; p = p.prev)
            if (p.waitStatus <= 0)
                return true;
        return false;
    }

    public final boolean hasContended() {
        return head != null;
    }

    public final Thread getFirstQueuedThread() {
        // handle only fast path, else relay
        return (head == tail) ? null : fullGetFirstQueuedThread();
    }

    private Thread fullGetFirstQueuedThread() {
        Node h, s;
        Thread st;
        if (((h = head) != null && (s = h.next) != null && s.prev == head && (st = s.thread) != null) ||
            ((h = head) != null && (s = h.next) != null && s.prev == head && (st = s.thread) != null))
            return st;

        Thread firstThread = null;
        for (Node p = tail; p != null && p != head; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                firstThread = t;
        }
        return firstThread;
    }

    public final boolean isQueued(Thread thread) {
        if (thread == null)
            throw new NullPointerException();
        for (Node p = tail; p != null; p = p.prev)
            if (p.thread == thread)
                return true;
        return false;
    }

    final boolean apparentlyFirstQueuedIsExclusive() {
        Node h, s;
        return (h = head) != null && (s = h.next) != null && !s.isShared() && s.thread != null;
    }

    /**
     * 是通过判断"当前线程"是不是在CLH队列的队首，来返回AQS中是不是有比“当前线程”等待更久的线程。
     * @date 2022/6/19 18:02
     * @param
     * @return boolean
     */
    public final boolean hasQueuedPredecessors() {
        Node h, s;
        if ((h = head) != null) {
            if ((s = h.next) == null || s.waitStatus > 0) {
                s = null; // traverse in case of concurrent cancellation
                for (Node p = tail; p != h && p != null; p = p.prev) {
                    if (p.waitStatus <= 0)
                        s = p;
                }
            }
            if (s != null && s.thread != Thread.currentThread())
                return true;
        }
        return false;
    }

    // Instrumentation and monitoring methods

    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread != null)
                ++n;
        }
        return n;
    }

    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    public final Collection<Thread> getExclusiveQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    public String toString() {
        return super.toString() + "[State = " + getState() + ", " + (hasQueuedThreads() ? "non" : "") + "empty queue]";
    }

    // Internal support methods for Conditions

    /**
     * 
     * @date 2022/6/17 21:17
     * @param node
     * @return boolean
     */
    final boolean isOnSyncQueue(Node node) {
        // 状态为Condition，获取前驱节点为null，返回false
        if (node.waitStatus == Node.CONDITION || node.prev == null)
            return false;
        // 后继节点不为null，肯定在CLH同步队列中，因为如果Node中pre和next变量标记AQS阻塞队列的前驱和后继
        // nextWaiter记录条件等待队列上的后继，所以next只有在进入到AQS阻塞队列才会设置
        if (node.next != null)
            return true;
        // 其他情况，从AQS阻塞队列的队尾开始遍历，查看当前节点是否在阻塞队列中，没想到这是什么情形。。。
        return findNodeFromTail(node);
    }

    private boolean findNodeFromTail(Node node) {
        // We check for node first, since it's likely to be at or near tail.
        // tail is known to be non-null, so we could re-order to "save"
        // one null check, but we leave it this way to help the VM.
        for (Node p = tail; ; ) {
            if (p == node)
                return true;
            if (p == null)
                return false;
            p = p.prev;
        }
    }

    /**
     *
     * @date 2022/6/17 21:19
     * @param node
     * @return boolean
     */
    final boolean transferForSignal(Node node) {
        // If cannot change waitStatus, the node has been cancelled.
        if (!node.compareAndSetWaitStatus(Node.CONDITION, 0))
            return false;

        // 将P加入到AQS阻塞队列中 -- 先把Node放入互斥锁的同步队列中，再调用unpark方法
        Node p = enq(node);
        int ws = p.waitStatus;
        if (ws > 0 || !p.compareAndSetWaitStatus(ws, Node.SIGNAL))
            LockSupport.unpark(node.thread);
        return true;
    }

    final boolean transferAfterCancelledWait(Node node) {
        if (node.compareAndSetWaitStatus(Node.CONDITION, 0)) {
            enq(node);
            return true;
        }
        /*
         * If we lost out to a signal(), then we can't proceed
         * until it finishes its enq().  Cancelling during an
         * incomplete transfer is both rare and transient, so just
         * spin.
         */
        while (!isOnSyncQueue(node))
            Thread.yield();
        return false;
    }

    /**
     *
     * @date 2022/6/17 21:16
     * @param node
     * @return int
     */
    final int fullyRelease(Node node) {
        try {
            // 获取当前的重入次数
            int savedState = getState();
            if (release(savedState))
                return savedState;
            throw new IllegalMonitorStateException();
        } catch (Throwable t) {
            node.waitStatus = Node.CANCELLED;
            throw t;
        }
    }

    // Instrumentation methods for conditions

    public final boolean owns(ConditionObject condition) {
        return condition.isOwnedBy(this);
    }

    public final boolean hasWaiters(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.hasWaiters();
    }

    public final int getWaitQueueLength(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitQueueLength();
    }

    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitingThreads();
    }

    // VarHandle mechanics
    private static final VarHandle STATE;
    private static final VarHandle HEAD;
    private static final VarHandle TAIL;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            STATE = l.findVarHandle(AbstractQueuedSynchronizer.class, "state", int.class);
            HEAD = l.findVarHandle(AbstractQueuedSynchronizer.class, "head", Node.class);
            TAIL = l.findVarHandle(AbstractQueuedSynchronizer.class, "tail", Node.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        // Reduce the risk of rare disastrous classloading in first call to
        // LockSupport.park: https://bugs.openjdk.java.net/browse/JDK-8074773
        Class<?> ensureLoaded = LockSupport.class;
    }

    private final void initializeSyncQueue() {
        Node h;
        if (HEAD.compareAndSet(this, null, (h = new Node())))
            tail = h;
    }

    private final boolean compareAndSetTail(Node expect, Node update) {
        return TAIL.compareAndSet(this, expect, update);
    }
}
