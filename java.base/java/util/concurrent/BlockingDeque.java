/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */


package java.util.concurrent;

import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 阻塞的双端队列接口
 * 继承了BlockingQueue接口，同时增加了对应的双端队列操作接口。该接口只有一个实现，就是LinkedBlockingDeque。
 * @author liuzhen
 * @date 2022/4/15 23:50
 * @return
 */
public interface BlockingDeque<E> extends BlockingQueue<E>, Deque<E> {

    // 添加元素 ---------------------------------------------------------------->
    // first ----------------------------->
    void push(E e);

    void addFirst(E e);

    boolean offerFirst(E e);

    boolean offerFirst(E e, long timeout, TimeUnit unit) throws InterruptedException;

    void putFirst(E e) throws InterruptedException;

    // last ----------------------------->
    boolean add(E e);

    void addLast(E e);

    boolean offer(E e);

    boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException;

    boolean offerLast(E e);

    boolean offerLast(E e, long timeout, TimeUnit unit) throws InterruptedException;

    void put(E e) throws InterruptedException;

    void putLast(E e) throws InterruptedException;


    // 删除元素 ---------------------------------------------------------------->
    // first ----------------------------->
    E remove();

    boolean remove(Object o);

    E take() throws InterruptedException;

    E takeFirst() throws InterruptedException;

    E poll();

    E poll(long timeout, TimeUnit unit) throws InterruptedException;

    E pollFirst(long timeout, TimeUnit unit) throws InterruptedException;

    boolean removeFirstOccurrence(Object o);
    // last ----------------------------->

    E takeLast() throws InterruptedException;

    E pollLast(long timeout, TimeUnit unit) throws InterruptedException;

    boolean removeLastOccurrence(Object o);

    // 获取元素 ---------------------------------------------------------------->
    E peek();

    E element();

    // ---------------------------------------------------------------->

    int size();

    boolean contains(Object o);

    Iterator<E> iterator();

    // *** Stack methods ***

}
