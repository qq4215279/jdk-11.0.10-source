/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent;

import java.util.Collection;
import java.util.Queue;

/**
 * 阻塞队列
 * @author liuzhen
 * @date 2022/4/15 22:40
 * @return
 */
public interface BlockingQueue<E> extends Queue<E> {

    // 入队api：add(E e)  offer(E e)  put(E e)
    /**
     * add(...)和offer(..)的
     * 返回值是布尔类型，而put无返回值，还会抛出中断异常，所以add(...)和offer(..)是无阻塞的，也是
     * Queue本身定义的接口，而put(..)是阻塞的。add(...)和offer(..)的区别不大，当队列为满的时候，前者会
     * 抛出异常，后者则直接返回false。
     * @author liuzhen
     * @date 2022/4/15 22:46
     * @param e
     * @return boolean
     */
    boolean add(E e);

    boolean offer(E e);

    boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException;

    /**
     *
     * @author liuzhen
     * @date 2022/4/15 22:47
     * @param e
     * @return void
     */
    void put(E e) throws InterruptedException;


    // -------------------------------------------->

    // 出队列与之类似，提供了remove()、poll()、take()等方法，remove()是非阻塞式的，take()和poll()是阻塞式的。

    boolean remove(Object o);

    E poll(long timeout, TimeUnit unit) throws InterruptedException;

    E take() throws InterruptedException;

    // -------------------------------------------->

    int remainingCapacity();

    boolean contains(Object o);

    int drainTo(Collection<? super E> c);

    int drainTo(Collection<? super E> c, int maxElements);
}
