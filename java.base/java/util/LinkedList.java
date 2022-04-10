/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util;

import java.util.function.Consumer;

/**
 * 和 ArrayList 集合⼀样，LinkedList 集合也实现了Cloneable接⼝和Serializable接⼝，分别⽤来⽀持克隆以及⽀持序列化。List 接⼝也不⽤多说，定义了⼀套 List 集合类型的⽅法规范。
 * 注意，相对于 ArrayList 集合，LinkedList 集合多实现了⼀个 Deque 接⼝，这是⼀个双向队列接⼝，双向队列就是两端都可以进⾏增加和删除操作。
 */
public class LinkedList<E> extends AbstractSequentialList<E> implements List<E>, Deque<E>, Cloneable, java.io.Serializable {
    /** 链表元素（节点）的个数 */
    transient int size = 0;
    /** 指向第⼀个节点的指针 */
    transient Node<E> first;
    /** 指向最后⼀个节点的指针 */
    transient Node<E> last;

    /*
    void dataStructureInvariants() {
        assert (size == 0)
            ? (first == null && last == null)
            : (first.prev == null && last.next == null);
    }
    */

    /**
     * 默认的空的构造函数
     */
    public LinkedList() {
    }

    /**
     * 将已有元素的集合Collection的实例添加到 LinkedList 中，调⽤的是 addAll() ⽅法
     * 注意：LinkedList 是没有初始化链表⼤⼩的构造函数，因为链表不像数组，⼀个定义好的数组是必须要有确定的⼤⼩，然后去分配内存空间，
     * ⽽链表不⼀样，它没有确定的⼤⼩，通过指针的移动来指向下⼀个内存地址的分配。
     * @param c
     */
    public LinkedList(Collection<? extends E> c) {
        this();
        addAll(c);
    }

    /**
     * 删除元素
     * @author liuzhen
     * @date 2022/4/9 21:39
     * @param
     * @return E
     */
    public E remove() {
        return removeFirst();
    }

    public E remove(int index) {
        checkElementIndex(index);

        return unlink(node(index));
    }

    /**
     *
     * 如果存在，则从该列表中删除指定元素的第⼀次出现
     * 此⽅法本质上和 remove(int index) 没多⼤区别，通过循环判断元素进⾏删除，需要注意的是，是删除第⼀次出现的元素，不是所有的
     * @author liuzhen
     * @date 2022/4/9 22:01
     * @param o
     * @return boolean
     */
    public boolean remove(Object o) {
        if (o == null) {
            for (Node<E> x = first; x != null; x = x.next) {
                if (x.item == null) {
                    unlink(x);
                    return true;
                }
            }
        } else {
            for (Node<E> x = first; x != null; x = x.next) {
                if (o.equals(x.item)) {
                    unlink(x);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     *
     */
    E unlink(Node<E> x) {
        // assert x != null;
        final E element = x.item;
        final Node<E> next = x.next;
        final Node<E> prev = x.prev;

        if (prev == null) {
            first = next;
        } else {
            prev.next = next;
            x.prev = null;
        }

        if (next == null) {
            last = prev;
        } else {
            next.prev = prev;
            x.next = null;
        }

        x.item = null;
        size--;
        modCount++;
        return element;
    }

    /**
     * 从此列表中移除并返回第⼀个元素
     */
    public E removeFirst() {
        // f设为头结点
        final Node<E> f = first;
        // 如果头结点为空，则抛出异常
        if (f == null)
            throw new NoSuchElementException();
        return unlinkFirst(f);
    }

    private E unlinkFirst(Node<E> f) {
        // assert f == first && f != null;
        final E element = f.item;
        // next 为头结点的下⼀个节点
        final Node<E> next = f.next;
        f.item = null;
        // 将节点的元素以及引⽤都设为 null，便于垃圾回收
        f.next = null; // help GC
        // 修改头结点为第⼆个节点
        first = next;

        // 如果第⼆个节点为空（当前链表只存在第⼀个元素）
        if (next == null) {
            // 那么尾节点也置为 null
            last = null;
        } else {
            // 如果第⼆个节点不为空，那么将第⼆个节点的上⼀个引⽤置为 null
            next.prev = null;
        }

        size--;
        modCount++;
        return element;
    }

    /**
     * 从该列表中删除并返回最后⼀个元素
     * @author liuzhen
     * @date 2022/4/9 21:44
     * @param
     * @return E
     */
    public E removeLast() {
        final Node<E> l = last;
        // 如果尾节点为空，表示当前集合为空，抛出异常
        if (l == null)
            throw new NoSuchElementException();

        return unlinkLast(l);
    }

    /**
     *
     */
    private E unlinkLast(Node<E> l) {
        // assert l == last && l != null;
        final E element = l.item;
        final Node<E> prev = l.prev;
        l.item = null;
        // 将节点的元素以及引⽤都设为 null，便于垃圾回收
        l.prev = null; // help GC
        // 尾节点为倒数第⼆个节点
        last = prev;

        // 如果倒数第⼆个节点为null
        if (prev == null) {
            // 那么将节点也置为 null
            first = null;
        } else {
            // 如果倒数第⼆个节点不为空，那么将倒数第⼆个节点的下⼀个引⽤置为 null
            prev.next = null;
        }

        size--;
        modCount++;

        return element;
    }

    /** 
     * 添加元素到默认
     * @author liuzhen
     * @date 2022/4/9 21:22 
     * @param e 
     * @return boolean
     */
    public boolean add(E e) {
        linkLast(e);
        return true;
    }

    /**
     * 将指定的元素插⼊此列表中的指定位置
     * @author liuzhen
     * @date 2022/4/9 21:22
     * @param index
     * @param element
     * @return void
     */
    public void add(int index, E element) {
        // //判断索引 index >= 0 && index <= size中时抛出IndexOutOfBoundsException异常
        checkPositionIndex(index);

        // 如果索引值等于链表⼤⼩
        if (index == size) {
            // 将节点插⼊到尾节点
            linkLast(element);
        } else {
            //
            linkBefore(element, node(index));
        }
    }

    /**
     * 将指定的元素附加到链表头节点
     * @author liuzhen
     * @date 2022/4/9 21:14
     * @param e
     * @return void
     */
    public void addFirst(E e) {
        linkFirst(e);
    }

    /**
     * 将指定的元素附加到链表头节点
     */
    private void linkFirst(E e) {
        // 将头节点赋值给 f
        final Node<E> f = first;
        // 将指定元素构造成⼀个新节点，此节点的指向下⼀个节点的引⽤为头节点
        final Node<E> newNode = new Node<>(null, e, f);
        // 将新节点设为头节点，那么原先的头节点 f 变为第⼆个节点
        first = newNode;
        // 如果第⼆个节点为空，也就是原先链表是空
        if (f == null) {
            // 将这个新节点也设为尾节点（前⾯已经设为头节点了）
            last = newNode;
        } else {
            // 将原先的头节点的上⼀个节点指向新节点
            f.prev = newNode;
        }

        // 节点数加1
        size++;
        // 和ArrayList中⼀样，iterator和listIterator⽅法返回的迭代器和列表迭代器实现使⽤
        modCount++;
    }

    /**
     * 将指定元素添加到链表尾
     * @author liuzhen
     * @date 2022/4/9 21:16
     * @param e
     * @return void
     */
    public void addLast(E e) {
        linkLast(e);
    }

    /**
     * 将节点插⼊到尾节点
     */
    void linkLast(E e) {
        // 将l设为尾节点
        final Node<E> l = last;
        // 构造⼀个新节点，节点上⼀个节点引⽤指向尾节点l
        final Node<E> newNode = new Node<>(l, e, null);
        // 将尾节点设为创建的新节点
        last = newNode;
        // 如果尾节点为空，表示原先链表为空
        if (l == null) {
            // 将头节点设为新创建的节点（尾节点也是新创建的节点）
            first = newNode;
        } else {
            // 将原来尾节点下⼀个节点的引⽤指向新节点
            l.next = newNode;
        }

        // 节点数加1
        size++;
        // 和ArrayList中⼀样，iterator和listIterator⽅法返回的迭代器和列表迭代器实现使⽤。
        modCount++;
    }

    /**
     *
     */
    void linkBefore(E e, Node<E> succ) {
        // 将pred设为插⼊节点的上⼀个节点
        final Node<E> pred = succ.prev;
        // 将新节点的上引⽤设为pred,下引⽤设为succ
        final Node<E> newNode = new Node<>(pred, e, succ);
        // succ的上⼀个节点的引⽤设为新节点
        succ.prev = newNode;
        // 如果插⼊节点的上⼀个节点引⽤为空
        if (pred == null) {
            // 新节点就是头节点
            first = newNode;
        } else {
            // 插⼊节点的下⼀个节点引⽤设为新节点
            pred.next = newNode;
        }

        size++;
        modCount++;
    }

    /**
     * 找到index里的node
     * @param index
     * @return
     */
    Node<E> node(int index) {
        // 如果插⼊的索引在前半部分
        if (index < (size >> 1)) {
            // 设x为头节点
            Node<E> x = first;
            //从开始节点到插⼊节点索引之间的所有节点向后移动⼀位
            for (int i = 0; i < index; i++)
                x = x.next;
            return x;
        } else { // 如果插⼊节点位置在后半部分
            // 将x设为最后⼀个节点
            Node<E> x = last;
            // 从最后节点到插⼊节点的索引位置之间的所有节点向前移动⼀位
            for (int i = size - 1; i > index; i--)
                x = x.prev;
            return x;
        }
    }

    /**
     * 按照指定集合的迭代器返回的顺序，将指定集合中的所有元素追加到此列表的末尾。
     * 按照指定集合的迭代器返回的顺序，将指定集合中的所有元素追加到此列表的末尾此⽅法还有⼀个 addAll(int index, Collection<? extends E> c)，将集合 c 中所有元素插⼊到指定索引的位置。
     * 其实 addAll(Collection<? extends E> c) == addAll(size, Collection<? extends E> c)
     * @author liuzhen
     * @date 2022/4/9 21:33
     * @param c
     * @return boolean
     */
    public boolean addAll(Collection<? extends E> c) {
        return addAll(size, c);
    }

    /**
     * 将集合 c 中所有元素插⼊到指定索引的位置。
     * 看到下面向 LinkedList 集合中添加元素的各种⽅式，我们发现LinkedList 每次添加元素只是改变元素的上⼀个指针引⽤和下⼀个指针引⽤，⽽且没有扩容。
     * 对⽐于 ArrayList ，需要扩容，⽽且在中间插⼊元素时，后⾯的所有元素都要移动⼀位，两者插⼊元素时的效率差异很⼤，
     * 注意：每次进⾏添加操作，都有modCount++ 的操作。
     * @author liuzhen
     * @date 2022/4/9 21:33
     * @param index
     * @param c
     * @return boolean
     */
    public boolean addAll(int index, Collection<? extends E> c) {
        // 判断索引 index >= 0 && index <= size 中时抛出IndexOutOfBoundsException异常
        checkPositionIndex(index);

        // 将集合转换成⼀个 Object 类型的数组
        Object[] a = c.toArray();
        int numNew = a.length;
        // 如果添加的集合为空，直接返回false
        if (numNew == 0)
            return false;

        Node<E> pred, succ;
        // 如果插⼊的位置等于链表的⻓度，就是将原集合元素附加到链表的末尾
        if (index == size) {
            succ = null;
            pred = last;
        } else {
            succ = node(index);
            pred = succ.prev;
        }

        // 遍历要插⼊的元素
        for (Object o : a) {
            @SuppressWarnings("unchecked") E e = (E)o;
            Node<E> newNode = new Node<>(pred, e, null);
            if (pred == null)
                first = newNode;
            else
                pred.next = newNode;
            pred = newNode;
        }

        if (succ == null) {
            last = pred;
        } else {
            pred.next = succ;
            succ.prev = pred;
        }

        size += numNew;
        modCount++;
        return true;
    }

    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    public int size() {
        return size;
    }

    public void clear() {
        // Clearing all of the links between nodes is "unnecessary", but:
        // - helps a generational GC if the discarded nodes inhabit
        //   more than one generation
        // - is sure to free memory even if there is a reachable Iterator
        for (Node<E> x = first; x != null; ) {
            Node<E> next = x.next;
            x.item = null;
            x.next = null;
            x.prev = null;
            x = next;
        }
        first = last = null;
        size = 0;
        modCount++;
    }

    // Positional Access Operations

    /**
     * 返回指定索引处的元素
     *
     * 但是需要注意的是， get(int index) ⽅法每次都要遍历该索引之前的所有元素，这句话这么理解：
     * 　　⽐如上⾯的⼀个 LinkedList 集合，我放⼊了 A,B,C,D是个元素。总共需要四次遍历：
     * 　　第⼀次遍历打印 A：只需遍历⼀次。
     * 　　第⼆次遍历打印 B：需要先找到 A，然后再找到 B 打印。
     * 　　第三次遍历打印 C：需要先找到 A，然后找到 B，最后找到 C 打印。
     * 　　第四次遍历打印 D：需要先找到 A，然后找到 B，然后找到 C，最后找到 D。
     * 　　这样如果集合元素很多，越查找到后⾯（当然此处的get⽅法进⾏了优化，查找前半部分从前⾯开始
     * 遍历，查找后半部分从后⾯开始遍历，但是需要的时间还是很多）花费的时间越多。那么如何改进呢？
     * -> 迭代器
     * @author liuzhen
     * @date 2022/4/9 22:19
     * @param index
     * @return E
     */
    public E get(int index) {
        // 判断索引 index >= 0 && index <= size中时抛出IndexOutOfBoundsException异常
        checkElementIndex(index);
        return node(index).item;
    }

    /**
     * 返回此列表中的第⼀个元素
     * @author liuzhen
     * @date 2022/4/9 22:21
     * @param
     * @return E
     */
    public E getFirst() {
        final Node<E> f = first;
        if (f == null)
            throw new NoSuchElementException();
        return f.item;
    }

    /**
     * 返回此列表中的最后⼀个元素
     * @author liuzhen
     * @date 2022/4/9 22:21
     * @param
     * @return E
     */
    public E getLast() {
        final Node<E> l = last;
        if (l == null)
            throw new NoSuchElementException();
        return l.item;
    }

    /**
     *
     * 这⾥主要是通过 node(index) ⽅法获取指定索引位置的节点，然后修改此节点位置的元素即可。
     * @author liuzhen
     * @date 2022/4/9 22:19
     * @param index
     * @param element
     * @return E
     */
    public E set(int index, E element) {
        // 判断索引 index >= 0 && index <= size中时抛出IndexOutOfBoundsException异常
        checkElementIndex(index);

        // 获取指定索引处的元素
        Node<E> x = node(index);
        E oldVal = x.item;
        // 将指定位置的元素替换成要修改的元素
        x.item = element;
        // 返回指定索引位置原来的元素
        return oldVal;
    }

    private boolean isElementIndex(int index) {
        return index >= 0 && index < size;
    }

    private boolean isPositionIndex(int index) {
        return index >= 0 && index <= size;
    }

    private String outOfBoundsMsg(int index) {
        return "Index: " + index + ", Size: " + size;
    }

    private void checkElementIndex(int index) {
        if (!isElementIndex(index))
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    private void checkPositionIndex(int index) {
        if (!isPositionIndex(index))
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    // Search Operations

    /**
     * 返回此列表中指定元素第⼀次出现的索引，如果此列表不包含元素，则返回-1。
     * @author liuzhen
     * @date 2022/4/9 22:23
     * @param o
     * @return int
     */
    public int indexOf(Object o) {
        int index = 0;

        // 如果查找的元素为null(LinkedList可以允许null值)
        if (o == null) {
            // 从头结点开始不断向下⼀个节点进⾏遍历
            for (Node<E> x = first; x != null; x = x.next) {
                if (x.item == null)
                    return index;
                index++;
            }
        } else { // 如果查找的元素不为null
            for (Node<E> x = first; x != null; x = x.next) {
                if (o.equals(x.item))
                    return index;
                index++;
            }
        }

        // 找不到返回-1
        return -1;
    }

    public int lastIndexOf(Object o) {
        int index = size;
        if (o == null) {
            for (Node<E> x = last; x != null; x = x.prev) {
                index--;
                if (x.item == null)
                    return index;
            }
        } else {
            for (Node<E> x = last; x != null; x = x.prev) {
                index--;
                if (o.equals(x.item))
                    return index;
            }
        }
        return -1;
    }

    // Queue operations.

    public E peek() {
        final Node<E> f = first;
        return (f == null) ? null : f.item;
    }

    public E element() {
        return getFirst();
    }

    public E poll() {
        final Node<E> f = first;
        return (f == null) ? null : unlinkFirst(f);
    }

    public boolean offer(E e) {
        return add(e);
    }

    // Deque operations

    public boolean offerFirst(E e) {
        addFirst(e);
        return true;
    }

    public boolean offerLast(E e) {
        addLast(e);
        return true;
    }

    public E peekFirst() {
        final Node<E> f = first;
        return (f == null) ? null : f.item;
    }

    public E peekLast() {
        final Node<E> l = last;
        return (l == null) ? null : l.item;
    }

    public E pollFirst() {
        final Node<E> f = first;
        return (f == null) ? null : unlinkFirst(f);
    }

    public E pollLast() {
        final Node<E> l = last;
        return (l == null) ? null : unlinkLast(l);
    }

    public void push(E e) {
        addFirst(e);
    }

    public E pop() {
        return removeFirst();
    }

    public boolean removeFirstOccurrence(Object o) {
        return remove(o);
    }

    public boolean removeLastOccurrence(Object o) {
        if (o == null) {
            for (Node<E> x = last; x != null; x = x.prev) {
                if (x.item == null) {
                    unlink(x);
                    return true;
                }
            }
        } else {
            for (Node<E> x = last; x != null; x = x.prev) {
                if (o.equals(x.item)) {
                    unlink(x);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     *
     */
    public ListIterator<E> listIterator(int index) {
        checkPositionIndex(index);
        return new ListItr(index);
    }

    /**
     * 内部类 ListItr，⽅法实现⼤体上也差不多，通过移动游标指向每⼀次要遍历的元素，不⽤在遍历某个元素之前都要从头开始。其⽅法实现也⽐较简单：
     */
    private class ListItr implements ListIterator<E> {
        private Node<E> lastReturned;
        private Node<E> next;
        private int nextIndex;
        private int expectedModCount = modCount;

        ListItr(int index) {
            // assert isPositionIndex(index);
            next = (index == size) ? null : node(index);
            nextIndex = index;
        }

        public boolean hasNext() {
            return nextIndex < size;
        }

        public E next() {
            checkForComodification();
            if (!hasNext())
                throw new NoSuchElementException();

            lastReturned = next;
            next = next.next;
            nextIndex++;
            return lastReturned.item;
        }

        public boolean hasPrevious() {
            return nextIndex > 0;
        }

        public E previous() {
            checkForComodification();
            if (!hasPrevious())
                throw new NoSuchElementException();

            lastReturned = next = (next == null) ? last : next.prev;
            nextIndex--;
            return lastReturned.item;
        }

        public int nextIndex() {
            return nextIndex;
        }

        public int previousIndex() {
            return nextIndex - 1;
        }

        public void remove() {
            checkForComodification();
            if (lastReturned == null)
                throw new IllegalStateException();

            Node<E> lastNext = lastReturned.next;
            unlink(lastReturned);
            if (next == lastReturned)
                next = lastNext;
            else
                nextIndex--;
            lastReturned = null;
            expectedModCount++;
        }

        public void set(E e) {
            if (lastReturned == null)
                throw new IllegalStateException();
            checkForComodification();
            lastReturned.item = e;
        }

        public void add(E e) {
            checkForComodification();
            lastReturned = null;
            if (next == null)
                linkLast(e);
            else
                linkBefore(e, next);
            nextIndex++;
            expectedModCount++;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Objects.requireNonNull(action);
            while (modCount == expectedModCount && nextIndex < size) {
                action.accept(next.item);
                lastReturned = next;
                next = next.next;
                nextIndex++;
            }
            checkForComodification();
        }

        /**
         * 注意的是 modCount 字段，前⾯我们在增加和删除元素的时候，都会进⾏⾃增操作modCount，
         * 这是因为如果想⼀边迭代，⼀边⽤集合⾃带的⽅法进⾏删除或者新增操作，都会抛出异常。（使⽤迭代器的增删⽅法不会抛异常）
         */
        final void checkForComodification() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }
    }

    /**
     * Node
     * 注意这⾥出现了⼀个 Node 类，这是 LinkedList 类中的⼀个内部类，其中每⼀个元素就代表⼀个Node 类对象，
     * LinkedList 集合就是由许多个 Node 对象类似于⼿拉着⼿构成。
     * @author liuzhen
     * @date 2022/4/9 21:08
     * @return
     */
    private static class Node<E> {
        /** 实际存储的元素 */
        E item;
        /** 指向上⼀个节点的引⽤ */
        Node<E> next;
        /** 指向下⼀个节点的引⽤ */
        Node<E> prev;

        Node(Node<E> prev, E element, Node<E> next) {
            this.item = element;
            this.next = next;
            this.prev = prev;
        }
    }

    /**
     * 通过适配器模式实现的接⼝，作⽤是倒叙打印链表
     */
    public Iterator<E> descendingIterator() {
        return new DescendingIterator();
    }

    /**
     * 迭代器
     */
    private class DescendingIterator implements Iterator<E> {
        private final ListItr itr = new ListItr(size());

        public boolean hasNext() {
            return itr.hasPrevious();
        }

        public E next() {
            return itr.previous();
        }

        public void remove() {
            itr.remove();
        }
    }

    @SuppressWarnings("unchecked")
    private LinkedList<E> superClone() {
        try {
            return (LinkedList<E>)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    public Object clone() {
        LinkedList<E> clone = superClone();

        // Put clone into "virgin" state
        clone.first = clone.last = null;
        clone.size = 0;
        clone.modCount = 0;

        // Initialize clone with our elements
        for (Node<E> x = first; x != null; x = x.next)
            clone.add(x.item);

        return clone;
    }

    public Object[] toArray() {
        Object[] result = new Object[size];
        int i = 0;
        for (Node<E> x = first; x != null; x = x.next)
            result[i++] = x.item;
        return result;
    }

    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        if (a.length < size)
            a = (T[])java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
        int i = 0;
        Object[] result = a;
        for (Node<E> x = first; x != null; x = x.next)
            result[i++] = x.item;

        if (a.length > size)
            a[size] = null;

        return a;
    }

    private static final long serialVersionUID = 876323262645176354L;

    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        // Write out any hidden serialization magic
        s.defaultWriteObject();

        // Write out size
        s.writeInt(size);

        // Write out all elements in the proper order.
        for (Node<E> x = first; x != null; x = x.next)
            s.writeObject(x.item);
    }

    @SuppressWarnings("unchecked")
    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        // Read in any hidden serialization magic
        s.defaultReadObject();

        // Read in size
        int size = s.readInt();

        // Read in all elements in the proper order.
        for (int i = 0; i < size; i++)
            linkLast((E)s.readObject());
    }

    @Override
    public Spliterator<E> spliterator() {
        return new LLSpliterator<>(this, -1, 0);
    }

    static final class LLSpliterator<E> implements Spliterator<E> {
        static final int BATCH_UNIT = 1 << 10;  // batch array size increment
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        final LinkedList<E> list; // null OK unless traversed
        Node<E> current;      // current node; null until initialized
        int est;              // size estimate; -1 until first needed
        int expectedModCount; // initialized when est set
        int batch;            // batch size for splits

        LLSpliterator(LinkedList<E> list, int est, int expectedModCount) {
            this.list = list;
            this.est = est;
            this.expectedModCount = expectedModCount;
        }

        final int getEst() {
            int s; // force initialization
            final LinkedList<E> lst;
            if ((s = est) < 0) {
                if ((lst = list) == null)
                    s = est = 0;
                else {
                    expectedModCount = lst.modCount;
                    current = lst.first;
                    s = est = lst.size;
                }
            }
            return s;
        }

        public long estimateSize() {
            return (long)getEst();
        }

        public Spliterator<E> trySplit() {
            Node<E> p;
            int s = getEst();
            if (s > 1 && (p = current) != null) {
                int n = batch + BATCH_UNIT;
                if (n > s)
                    n = s;
                if (n > MAX_BATCH)
                    n = MAX_BATCH;
                Object[] a = new Object[n];
                int j = 0;
                do {
                    a[j++] = p.item;
                } while ((p = p.next) != null && j < n);
                current = p;
                batch = j;
                est = s - j;
                return Spliterators.spliterator(a, 0, j, Spliterator.ORDERED);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            Node<E> p;
            int n;
            if (action == null)
                throw new NullPointerException();
            if ((n = getEst()) > 0 && (p = current) != null) {
                current = null;
                est = 0;
                do {
                    E e = p.item;
                    p = p.next;
                    action.accept(e);
                } while (p != null && --n > 0);
            }
            if (list.modCount != expectedModCount)
                throw new ConcurrentModificationException();
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            Node<E> p;
            if (action == null)
                throw new NullPointerException();
            if (getEst() > 0 && (p = current) != null) {
                --est;
                E e = p.item;
                current = p.next;
                action.accept(e);
                if (list.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                return true;
            }
            return false;
        }

        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
        }
    }

}
