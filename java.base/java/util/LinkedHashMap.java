/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved. ORACLE PROPRIETARY/CONFIDENTIAL. Use is
 * subject to license terms.
 */

package java.util;

import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.io.IOException;

/**
 * LinkedHashMap 是基于 HashMap 实现的⼀种集合，具有 HashMap 集合上⾯所说的所有特点，除了 HashMap ⽆序的特点，LinkedHashMap 是有序的，
 * 因为 LinkedHashMap 在 HashMap 的基础上单独维护了⼀个具有所有数据的双向链表，该链表保证了元素迭代的顺序。
 * 所以我们可以直接这样说：LinkedHashMap = HashMap + LinkedList。LinkedHashMap 就是在HashMap 的基础上多维护了⼀个双向链表，⽤来保证元素迭代顺序。
 *
 * 注意：这⾥有五个属性别搞混淆的，对于 Node next 属性，是⽤来维护整个集合中 Entry 的顺序。
 * 对于 Entry before，Entry after ，以及 Entry head，Entry tail，这四个属性都是⽤来维护保证集合顺序的链表，
 * 其中前两个before和after表示某个节点的上⼀个节点和下⼀个节点，这是⼀个双向链表。
 * 后两个属性 head 和 tail 分别表示这个链表的头节点和尾节点。
 *
 * 1. 添加元素：LinkedHashMap 中是没有 put ⽅法的，直接调⽤⽗类 HashMap 的 put ⽅法。添加元素时重写了的4个⽅法：
 * - newNode(hash, key, value, null);
 * - putTreeVal(this, tab, hash, key, value)//newTreeNode(h, k, v, xpn)
 * - afterNodeAccess(e);
 * - afterNodeInsertion(evict);
 *
 * 2. 删除元素：同理也是调⽤ HashMap 的remove ⽅法。删除元素重写了 afterNodeRemoval() 方法。
 *
 * 3. 获取元素：get()
 * @author liuzhen
 * @date 2022/4/10 10:40
 */
public class LinkedHashMap<K, V> extends HashMap<K, V> implements Map<K, V> {

    private static final long serialVersionUID = 3801124242820219131L;

    /** ⽤来指向双向链表的头节点 */
    transient LinkedHashMap.Entry<K, V> head;

    /** ⽤来指向双向链表的尾节点 */
    transient LinkedHashMap.Entry<K, V> tail;

    /**
     * ⽤来指定LinkedHashMap的迭代顺序
     * true 表示按照访问顺序，会把访问过的元素放在链表后⾯，放置顺序是访问的顺序
     * false 表示按照插⼊顺序遍历
     */
    final boolean accessOrder;

    /**
     * LinkedHashMap 的每个元素都是⼀个 Entry，我们看到对于 Entry 继承⾃ HashMap 的 Node 结构，
     * 相对于 Node 结构，LinkedHashMap 多了 before 和 after 结构。
     * @param <K>
     * @param <V>
     */
    static class Entry<K, V> extends HashMap.Node<K, V> {
        /**  before 和 after 便是⽤来维护 LinkedHashMap 插⼊ Entry 的先后顺序的 */
        Entry<K, V> before, after;

        Entry(int hash, K key, V value, Node<K, V> next) {
            super(hash, key, value, next);
        }
    }

    /**
     * 无参构造
     * 调⽤⽆参的 HashMap 构造函数，具有默认初始容量（16）和加载因⼦（0.75）。并且设定了accessOrder = false，表示默认按照插⼊顺序进⾏遍历。
     */
    public LinkedHashMap() {
        super();
        accessOrder = false;
    }

    /**
     * 指定初始容量  并且设定了accessOrder = false，表示默认按照插⼊顺序进⾏遍历。
     * @param initialCapacity
     */
    public LinkedHashMap(int initialCapacity) {
        super(initialCapacity);
        accessOrder = false;
    }

    /**
     * 指定初始容量和加载因⼦  并且设定了accessOrder = false，表示默认按照插⼊顺序进⾏遍历。
     * @param initialCapacity
     * @param loadFactor
     */
    public LinkedHashMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
        accessOrder = false;
    }

    /**
     * 指定初始容量和加载因⼦，以及迭代规则
     * @param initialCapacity
     * @param loadFactor
     * @param accessOrder
     */
    public LinkedHashMap(int initialCapacity, float loadFactor, boolean accessOrder) {
        super(initialCapacity, loadFactor);
        this.accessOrder = accessOrder;
    }

    /**
     * 构造包含指定集合中的元素  并且设定了accessOrder = false，表示默认按照插⼊顺序进⾏遍历。
     * @param m
     */
    public LinkedHashMap(Map<? extends K, ? extends V> m) {
        super();
        accessOrder = false;
        putMapEntries(m, false);
    }

    private void transferLinks(LinkedHashMap.Entry<K, V> src, LinkedHashMap.Entry<K, V> dst) {
        LinkedHashMap.Entry<K, V> b = dst.before = src.before;
        LinkedHashMap.Entry<K, V> a = dst.after = src.after;
        if (b == null)
            head = dst;
        else
            b.after = dst;
        if (a == null)
            tail = dst;
        else
            a.before = dst;
    }

    void reinitialize() {
        super.reinitialize();
        head = tail = null;
    }

    /**
     * 重写
     *
     * @author liuzhen
     * @date 2022/4/24 22:36
     * @param hash
     * @param key
     * @param value
     * @param e
     * @return java.util.HashMap.Node<K,V>
     */
    Node<K, V> newNode(int hash, K key, V value, Node<K, V> e) {
        LinkedHashMap.Entry<K, V> p = new LinkedHashMap.Entry<>(hash, key, value, e);
        linkNodeLast(p);
        return p;
    }

    TreeNode<K, V> newTreeNode(int hash, K key, V value, Node<K, V> next) {
        TreeNode<K, V> p = new TreeNode<>(hash, key, value, next);
        linkNodeLast(p);
        return p;
    }

    /** 
     * 将当前添加的元素设为原始链表的尾节点，并维护链表节点之间的关系。　
     * @author liuzhen
     * @date 2022/4/10 10:52
     * @param p 
     * @return void
     */
    private void linkNodeLast(LinkedHashMap.Entry<K, V> p) {
        // ⽤临时变量last记录尾节点tail
        LinkedHashMap.Entry<K, V> last = tail;
        // 将尾节点设为当前插⼊的节点p
        tail = p;
        // 如果原先尾节点为null，表示当前链表为空
        if (last == null)
            // 头结点也为当前插⼊节点
            head = p;
        else { // 原始链表不为空
            // 那么将当前节点的上节点指向原始尾节点
            p.before = last;
            // 原始尾节点的下⼀个节点指向当前插⼊节点
            last.after = p;
        }
    }

    Node<K, V> replacementNode(Node<K, V> p, Node<K, V> next) {
        LinkedHashMap.Entry<K, V> q = (LinkedHashMap.Entry<K, V>)p;
        LinkedHashMap.Entry<K, V> t = new LinkedHashMap.Entry<>(q.hash, q.key, q.value, next);
        transferLinks(q, t);
        return t;
    }

    TreeNode<K, V> replacementTreeNode(Node<K, V> p, Node<K, V> next) {
        LinkedHashMap.Entry<K, V> q = (LinkedHashMap.Entry<K, V>)p;
        TreeNode<K, V> t = new TreeNode<>(q.hash, q.key, q.value, next);
        transferLinks(q, t);
        return t;
    }

    /**
     * 该⽅法⽤来移除最⽼的⾸节点
     * ⾸先⽅法要能执⾏到if语句⾥⾯，必须 evict = true，并且 头节点不为null，并且 removeEldestEntry(first) 返回true，这三个条件必须同时满⾜，
     * @author liuzhen
     * @date 2022/4/10 11:00
     * @param evict
     * @return void
     */
    void afterNodeInsertion(boolean evict) { // possibly remove eldest
        LinkedHashMap.Entry<K, V> first;
        if (evict && (first = head) != null && removeEldestEntry(first)) {
            K key = first.key;
            removeNode(hash(key), key, null, false, true);
        }
    }

    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        /*
        * 这就奇怪了，该⽅法直接返回的是 false，也就是说怎么都不会进⼊到 if ⽅法体内了，那这是这么回事呢？
        * 这其实是⽤来实现 LRU（Least Recently Used，最近最少使⽤）Cache 时，重写的⼀个⽅法。⽐如在 mybatis-connector 包中，有这样⼀个类：
        * public class LRUCache<K, V> extends LinkedHashMap<K, V> {
                private static final long serialVersionUID = 1L;
                protected int maxElements;
                public LRUCache(int maxSize) {
                    super(maxSize, 0.75F, true);
                    this.maxElements = maxSize;
                }
                protected boolean removeEldestEntry(Entry<K, V> eldest) {
                    return this.size() > this.maxElements;
                }
           }

          * 可以看到，它重写了 removeEldestEntry(Entry<K,V> eldest) ⽅法，当元素的个数⼤于设定的最⼤个数，便移除⾸元素。
        * */
        return false;
    }

    /**
     * 当我们删除某个节点时，为了保证链表还是有序的，那么必须维护其前后节点。⽽该⽅法的作⽤就是维护删除节点的前后节点关系。
     * @author liuzhen
     * @date 2022/4/10 11:05
     * @param e
     * @return void
     */
    void afterNodeRemoval(Node<K, V> e) { // unlink
        LinkedHashMap.Entry<K, V> p = (LinkedHashMap.Entry<K, V>)e, b = p.before, a = p.after;
        p.before = p.after = null;
        if (b == null)
            head = a;
        else
            b.after = a;
        if (a == null)
            tail = b;
        else
            a.before = b;
    }

    void internalWriteEntries(java.io.ObjectOutputStream s) throws IOException {
        for (LinkedHashMap.Entry<K, V> e = head; e != null; e = e.after) {
            s.writeObject(e.key);
            s.writeObject(e.value);
        }
    }

    public boolean containsValue(Object value) {
        for (LinkedHashMap.Entry<K, V> e = head; e != null; e = e.after) {
            V v = e.value;
            if (v == value || (value != null && value.equals(v)))
                return true;
        }
        return false;
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/10 11:06
     * @param key
     * @return V
     */
    public V get(Object key) {
        Node<K, V> e;
        if ((e = getNode(hash(key), key)) == null)
            return null;
        if (accessOrder)
            afterNodeAccess(e);
        return e.value;
    }

    public V getOrDefault(Object key, V defaultValue) {
        Node<K, V> e;
        if ((e = getNode(hash(key), key)) == null)
            return defaultValue;
        if (accessOrder)
            afterNodeAccess(e);
        return e.value;
    }

    /**
     * 把当前节点放到双向链表的尾部
     * 该⽅法是在 accessOrder = true 并且 插⼊的当前节点不等于尾节点时，该⽅法才会⽣效。并且该⽅法的作⽤是将插⼊的节点变为尾节点，后⾯在get⽅法中也会调⽤。
     * @author liuzhen
     * @date 2022/4/10 10:55
     * @param e
     * @return void
     */
    void afterNodeAccess(Node<K, V> e) { // move node to last
        LinkedHashMap.Entry<K, V> last;

        // 当 accessOrder = true 并且当前节点不等于尾节点tail。这⾥将last节点赋值为tail节点
        if (accessOrder && (last = tail) != e) {
            // 记录当前节点的上⼀个节点b和下⼀个节点a
            LinkedHashMap.Entry<K, V> p = (LinkedHashMap.Entry<K, V>)e, b = p.before, a = p.after;

            // 释放当前节点和后⼀个节点的关系
            p.after = null;
            if (b == null) {
                // 头节点=当前节点的下⼀个节点
                head = a;
            } else {
                // 否则b的后节点指向a
                b.after = a;
            }

            // 如果a != null
            if (a != null) {
                // a的前⼀个节点指向b
                a.before = b;
            } else {
                // b设为尾节点
                last = b;
            }

            // 如果尾节点为null
            if (last == null) {
                head = p;
            } else {
                // 否则将p放到双向链表的最后
                p.before = last;
                last.after = p;
            }

            // 将尾节点设为p
            tail = p;
            // LinkedHashMap对象操作次数+1，⽤于快速失败校验
            ++modCount;
        }
    }

    public void clear() {
        super.clear();
        head = tail = null;
    }

    public Set<K> keySet() {
        Set<K> ks = keySet;
        if (ks == null) {
            ks = new LinkedKeySet();
            keySet = ks;
        }
        return ks;
    }

    final class LinkedKeySet extends AbstractSet<K> {
        public final int size() {
            return size;
        }

        public final void clear() {
            LinkedHashMap.this.clear();
        }

        public final Iterator<K> iterator() {
            return new LinkedKeyIterator();
        }

        public final boolean contains(Object o) {
            return containsKey(o);
        }

        public final boolean remove(Object key) {
            return removeNode(hash(key), key, null, false, true) != null;
        }

        public final Spliterator<K> spliterator() {
            return Spliterators.spliterator(this, Spliterator.SIZED | Spliterator.ORDERED | Spliterator.DISTINCT);
        }

        public final void forEach(Consumer<? super K> action) {
            if (action == null)
                throw new NullPointerException();
            int mc = modCount;
            for (LinkedHashMap.Entry<K, V> e = head; e != null; e = e.after)
                action.accept(e.key);
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
    }

    public Collection<V> values() {
        Collection<V> vs = values;
        if (vs == null) {
            vs = new LinkedValues();
            values = vs;
        }
        return vs;
    }

    final class LinkedValues extends AbstractCollection<V> {
        public final int size() {
            return size;
        }

        public final void clear() {
            LinkedHashMap.this.clear();
        }

        public final Iterator<V> iterator() {
            return new LinkedValueIterator();
        }

        public final boolean contains(Object o) {
            return containsValue(o);
        }

        public final Spliterator<V> spliterator() {
            return Spliterators.spliterator(this, Spliterator.SIZED | Spliterator.ORDERED);
        }

        public final void forEach(Consumer<? super V> action) {
            if (action == null)
                throw new NullPointerException();
            int mc = modCount;
            for (LinkedHashMap.Entry<K, V> e = head; e != null; e = e.after)
                action.accept(e.value);
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
    }

    public Set<Map.Entry<K, V>> entrySet() {
        Set<Map.Entry<K, V>> es;
        return (es = entrySet) == null ? (entrySet = new LinkedEntrySet()) : es;
    }

    final class LinkedEntrySet extends AbstractSet<Map.Entry<K, V>> {
        public final int size() {
            return size;
        }

        public final void clear() {
            LinkedHashMap.this.clear();
        }

        public final Iterator<Map.Entry<K, V>> iterator() {
            return new LinkedEntryIterator();
        }

        public final boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>)o;
            Object key = e.getKey();
            Node<K, V> candidate = getNode(hash(key), key);
            return candidate != null && candidate.equals(e);
        }

        public final boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>)o;
                Object key = e.getKey();
                Object value = e.getValue();
                return removeNode(hash(key), key, value, true, true) != null;
            }
            return false;
        }

        public final Spliterator<Map.Entry<K, V>> spliterator() {
            return Spliterators.spliterator(this, Spliterator.SIZED | Spliterator.ORDERED | Spliterator.DISTINCT);
        }

        public final void forEach(Consumer<? super Map.Entry<K, V>> action) {
            if (action == null)
                throw new NullPointerException();
            int mc = modCount;
            for (LinkedHashMap.Entry<K, V> e = head; e != null; e = e.after)
                action.accept(e);
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
    }

    // Map overrides

    public void forEach(BiConsumer<? super K, ? super V> action) {
        if (action == null)
            throw new NullPointerException();
        int mc = modCount;
        for (LinkedHashMap.Entry<K, V> e = head; e != null; e = e.after)
            action.accept(e.key, e.value);
        if (modCount != mc)
            throw new ConcurrentModificationException();
    }

    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        if (function == null)
            throw new NullPointerException();
        int mc = modCount;
        for (LinkedHashMap.Entry<K, V> e = head; e != null; e = e.after)
            e.value = function.apply(e.key, e.value);
        if (modCount != mc)
            throw new ConcurrentModificationException();
    }

    // Iterators

    abstract class LinkedHashIterator {
        LinkedHashMap.Entry<K, V> next;
        LinkedHashMap.Entry<K, V> current;
        int expectedModCount;

        LinkedHashIterator() {
            next = head;
            expectedModCount = modCount;
            current = null;
        }

        public final boolean hasNext() {
            return next != null;
        }

        final LinkedHashMap.Entry<K, V> nextNode() {
            LinkedHashMap.Entry<K, V> e = next;
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            if (e == null)
                throw new NoSuchElementException();
            current = e;
            next = e.after;
            return e;
        }

        public final void remove() {
            Node<K, V> p = current;
            if (p == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            current = null;
            removeNode(p.hash, p.key, null, false, false);
            expectedModCount = modCount;
        }
    }

    final class LinkedKeyIterator extends LinkedHashIterator implements Iterator<K> {
        public final K next() {
            return nextNode().getKey();
        }
    }

    final class LinkedValueIterator extends LinkedHashIterator implements Iterator<V> {
        public final V next() {
            return nextNode().value;
        }
    }

    final class LinkedEntryIterator extends LinkedHashIterator implements Iterator<Map.Entry<K, V>> {
        public final Map.Entry<K, V> next() {
            return nextNode();
        }
    }

}
