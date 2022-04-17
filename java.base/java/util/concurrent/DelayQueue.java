/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */


package java.util.concurrent;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DelayQueue即延迟队列，也就是一个按延迟时间从小到大出队的PriorityQueue。
 * 所谓延迟时间，就是“未来将要执行的时间”减去“当前时间”。为此，放入DelayQueue中的元素，必须实现Delayed接口。
 * @author liuzhen
 * @date 2022/4/15 23:28
 * @return
 */
public class DelayQueue<E extends Delayed> extends AbstractQueue<E> implements BlockingQueue<E> {

    /** 一把锁和一个非空条件 */
    private final transient ReentrantLock lock = new ReentrantLock();
    private final Condition available = lock.newCondition();

    /** 优先级队列 */
    private final PriorityQueue<E> q = new PriorityQueue<E>();

    private Thread leader;


    public DelayQueue() {
    }

    public DelayQueue(Collection<? extends E> c) {
        this.addAll(c);
    }

    public boolean add(E e) {
        return offer(e);
    }

    public void put(E e) {
        offer(e);
    }

    public boolean offer(E e, long timeout, TimeUnit unit) {
        return offer(e);
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/15 23:35
     * @param e
     * @return boolean
     */
    public boolean offer(E e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            // 元素放入二叉堆
            q.offer(e);
            // 如果放进去的元素刚好是栈顶，延迟时间最小，通知其他线程；否则放入的元素不在堆顶，没有必要通知其他线程
            if (q.peek() == e) {
                leader = null;
                available.signal();
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public E poll() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            E first = q.peek();
            return (first == null || first.getDelay(NANOSECONDS) > 0) ? null : q.poll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 关于take()方法：
     * 1. 不同于一般的阻塞队列，只在队列为空的时候，才阻塞。如果堆顶元素的延迟时间没到，也会阻塞。
     * 2. 在上面的代码中使用了一个优化技术，用一个Thread leader变量记录了等待堆顶元素的第1个线程。为什么这样做呢？通过 getDelay(..)可以知道堆顶元素何时到期，
     * 不必无限期等待，可以使用condition.awaitNanos()等待一个有限的时间；只有当发现还有其他线程也在等待堆顶元素（leader！=NULL）时，才需要无限期等待。
     * @author liuzhen
     * @date 2022/4/15 23:30
     * @param
     * @return E
     */
    public E take() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            for (; ; ) {
                // 去除二叉堆的堆顶元素，即延迟时间最小的
                E first = q.peek();
                //  队列为空，take线程阻塞
                if (first == null)
                    available.await();
                else {
                    long delay = first.getDelay(NANOSECONDS);
                    // 堆顶元素的延迟时间小于等于0，出队列返回
                    if (delay <= 0L)
                        return q.poll();
                    first = null; // don't retain ref while waiting
                    // 若有其他线程也在等待改元素，则无限期等待
                    if (leader != null)
                        available.await();
                    else {
                        Thread thisThread = Thread.currentThread();
                        leader = thisThread;
                        try {
                            // 否则阻塞有限的时间
                            available.awaitNanos(delay);
                        } finally {
                            if (leader == thisThread)
                                leader = null;
                        }
                    }
                }
            }
        } finally {
            // 当前线程是leader，已经获取堆顶元素
            if (leader == null && q.peek() != null)
                // 唤醒其他线程
                available.signal();
            lock.unlock();
        }
    }

    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            for (; ; ) {
                E first = q.peek();
                if (first == null) {
                    if (nanos <= 0L)
                        return null;
                    else
                        nanos = available.awaitNanos(nanos);
                } else {
                    long delay = first.getDelay(NANOSECONDS);
                    if (delay <= 0L)
                        return q.poll();
                    if (nanos <= 0L)
                        return null;
                    first = null; // don't retain ref while waiting
                    if (nanos < delay || leader != null)
                        nanos = available.awaitNanos(nanos);
                    else {
                        Thread thisThread = Thread.currentThread();
                        leader = thisThread;
                        try {
                            long timeLeft = available.awaitNanos(delay);
                            nanos -= delay - timeLeft;
                        } finally {
                            if (leader == thisThread)
                                leader = null;
                        }
                    }
                }
            }
        } finally {
            if (leader == null && q.peek() != null)
                available.signal();
            lock.unlock();
        }
    }

    public E peek() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.peek();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.size();
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
            int n = 0;
            for (E first; n < maxElements && (first = q.peek()) != null && first.getDelay(NANOSECONDS) <= 0; ) {
                c.add(first);   // In this order, in case add() throws.
                q.poll();
                ++n;
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
            q.clear();
        } finally {
            lock.unlock();
        }
    }

    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    public Object[] toArray() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.toArray();
        } finally {
            lock.unlock();
        }
    }

    public <T> T[] toArray(T[] a) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.toArray(a);
        } finally {
            lock.unlock();
        }
    }

    public boolean remove(Object o) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.remove(o);
        } finally {
            lock.unlock();
        }
    }

    void removeEQ(Object o) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            for (Iterator<E> it = q.iterator(); it.hasNext(); ) {
                if (o == it.next()) {
                    it.remove();
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public Iterator<E> iterator() {
        return new Itr(toArray());
    }

    private class Itr implements Iterator<E> {
        final Object[] array; // Array of all elements
        int cursor;           // index of next element to return
        int lastRet;          // index of last element, or -1 if no such

        Itr(Object[] array) {
            lastRet = -1;
            this.array = array;
        }

        public boolean hasNext() {
            return cursor < array.length;
        }

        @SuppressWarnings("unchecked")
        public E next() {
            if (cursor >= array.length)
                throw new NoSuchElementException();
            return (E)array[lastRet = cursor++];
        }

        public void remove() {
            if (lastRet < 0)
                throw new IllegalStateException();
            removeEQ(array[lastRet]);
            lastRet = -1;
        }
    }

}
