/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved. ORACLE PROPRIETARY/CONFIDENTIAL. Use is
 * subject to license terms.
 */

package java.util;

import java.io.InvalidObjectException;

import jdk.internal.access.SharedSecrets;

/**
 * HashSet 是⼀个由 HashMap 实现的集合。元素⽆序且不能重复。
 * @author liuzhen
 * @date 2022/4/10 10:12
 */
public class HashSet<E> extends AbstractSet<E> implements Set<E>, Cloneable, java.io.Serializable {
    static final long serialVersionUID = -5024744406713321676L;

    /** HashSet集合中的内容是通过 HashMap 数据结构来存储的 */
    private transient HashMap<E, Object> map;

    /** 向HashSet中添加数据，数据在上⾯的 map 结构是作为 key 存在的，⽽ value 统⼀都是 PRESENT */
    private static final Object PRESENT = new Object();

    /**
     * 无参构造
     * 直接 new ⼀个 HashMap 对象出来，采⽤⽆参的 HashMap 构造函数，具有默认初始容量（16）和 加载因⼦（0.75）。
     */
    public HashSet() {
        map = new HashMap<>();
    }

    /**
     * 指定初始容量
     * @param initialCapacity
     */
    public HashSet(int initialCapacity) {
        map = new HashMap<>(initialCapacity);
    }

    /**
     * 指定初始容量和加载因⼦
     * 什么是加载因⼦？
     * 在 HashMap 中，能够存储元素的数量就是：总的容量*加载因⼦ ，新增⼀个元素时，如果HashMap集合中的元素⼤于前⾯公式计算的结果了，
     * 那么就必须要进⾏扩容操作，从时间和空间考虑，加载因⼦⼀般都选默认的0.75。
     * @param initialCapacity
     * @param loadFactor
     */
    public HashSet(int initialCapacity, float loadFactor) {
        map = new HashMap<>(initialCapacity, loadFactor);
    }

    HashSet(int initialCapacity, float loadFactor, boolean dummy) {
        map = new LinkedHashMap<>(initialCapacity, loadFactor);
    }

    /**
     * 构造包含指定集合中的元素
     * @param c
     */
    public HashSet(Collection<? extends E> c) {
        map = new HashMap<>(Math.max((int)(c.size() / .75f) + 1, 16));
        addAll(c);
    }

    public Iterator<E> iterator() {
        return map.keySet().iterator();
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * 查找元素
     * 调⽤ HashMap 的 containsKey(Object o) ⽅法，找到了返回 true，找不到返回 false。
     * @author liuzhen
     * @date 2022/4/10 10:19
     * @param o
     * @return boolean
     */
    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    /**
     * 添加元素
     * 通过 map.put() ⽅法来添加元素，如果新插⼊的key不存在，则返回null，如果新插⼊的key存在，则返回原key对应的value值（注意新插⼊的value会覆盖原value值）。
     * 也就是说 HashSet 的 add(E e) ⽅法，会将 e 作为 key，PRESENT 作为 value 插⼊到 map 集合中，如果 e 不存在，则插⼊成功返回 true;如果存在，则返回false。
     * @author liuzhen
     * @date 2022/4/10 10:17
     * @param e
     * @return boolean
     */
    public boolean add(E e) {
        return map.put(e, PRESENT) == null;
    }

    /**
     * 删除元素
     * 调⽤ HashMap 的remove(Object o) ⽅法，该⽅法会⾸先查找 map 集合中是否存在 o ，如果存在则删除，并返回该值，如果不存在则返回 null。
     * 也就是说 HashSet 的 remove(Object o) ⽅法，删除成功返回 true，删除的元素不存在会返回
     * false。
     * @author liuzhen
     * @date 2022/4/10 10:18
     * @param o
     * @return boolean
     */
    public boolean remove(Object o) {
        return map.remove(o) == PRESENT;
    }

    public void clear() {
        map.clear();
    }

    @SuppressWarnings("unchecked")
    public Object clone() {
        try {
            HashSet<E> newSet = (HashSet<E>)super.clone();
            newSet.map = (HashMap<E, Object>)map.clone();
            return newSet;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        // Write out any hidden serialization magic
        s.defaultWriteObject();

        // Write out HashMap capacity and load factor
        s.writeInt(map.capacity());
        s.writeFloat(map.loadFactor());

        // Write out size
        s.writeInt(map.size());

        // Write out all elements in the proper order.
        for (E e : map.keySet())
            s.writeObject(e);
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        // Read in any hidden serialization magic
        s.defaultReadObject();

        // Read capacity and verify non-negative.
        int capacity = s.readInt();
        if (capacity < 0) {
            throw new InvalidObjectException("Illegal capacity: " + capacity);
        }

        // Read load factor and verify positive and non NaN.
        float loadFactor = s.readFloat();
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new InvalidObjectException("Illegal load factor: " + loadFactor);
        }

        // Read size and verify non-negative.
        int size = s.readInt();
        if (size < 0) {
            throw new InvalidObjectException("Illegal size: " + size);
        }

        // Set the capacity according to the size and load factor ensuring that
        // the HashMap is at least 25% full but clamping to maximum capacity.
        capacity = (int)Math.min(size * Math.min(1 / loadFactor, 4.0f), HashMap.MAXIMUM_CAPACITY);

        // Constructing the backing map will lazily create an array when the first element is
        // added, so check it before construction. Call HashMap.tableSizeFor to compute the
        // actual allocation size. Check Map.Entry[].class since it's the nearest public type to
        // what is actually created.
        SharedSecrets.getJavaObjectInputStreamAccess().checkArray(s, Map.Entry[].class, HashMap.tableSizeFor(capacity));

        // Create backing HashMap
        map = (((HashSet<?>)this) instanceof LinkedHashSet ? new LinkedHashMap<>(capacity, loadFactor) : new HashMap<>(capacity, loadFactor));

        // Read in all elements in the proper order.
        for (int i = 0; i < size; i++) {
            @SuppressWarnings("unchecked") E e = (E)s.readObject();
            map.put(e, PRESENT);
        }
    }

    public Spliterator<E> spliterator() {
        return new HashMap.KeySpliterator<>(map, 0, -1, 0, 0);
    }
}
