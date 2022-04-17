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

    void addFirst(E e);

    void addLast(E e);

    boolean offerFirst(E e);

    boolean offerLast(E e);

    void putFirst(E e) throws InterruptedException;

    void putLast(E e) throws InterruptedException;

    boolean offerFirst(E e, long timeout, TimeUnit unit) throws InterruptedException;

    boolean offerLast(E e, long timeout, TimeUnit unit) throws InterruptedException;

    E takeFirst() throws InterruptedException;

    E takeLast() throws InterruptedException;

    E pollFirst(long timeout, TimeUnit unit) throws InterruptedException;

    E pollLast(long timeout, TimeUnit unit) throws InterruptedException;

    boolean removeFirstOccurrence(Object o);

    boolean removeLastOccurrence(Object o);

    // *** BlockingQueue methods ***

    boolean add(E e);

    boolean offer(E e);

    void put(E e) throws InterruptedException;

    boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException;

    E remove();

    E poll();

    E take() throws InterruptedException;

    E poll(long timeout, TimeUnit unit) throws InterruptedException;

    E element();

    E peek();

    boolean remove(Object o);

    boolean contains(Object o);

    int size();

    Iterator<E> iterator();

    // *** Stack methods ***

    void push(E e);
}
