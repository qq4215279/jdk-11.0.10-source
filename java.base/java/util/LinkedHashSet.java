/*
 * Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved. ORACLE PROPRIETARY/CONFIDENTIAL. Use is
 * subject to license terms.
 */

package java.util;

/**
 *
 * @author liuzhen
 * @date 2022/4/10 11:07
 * @return
 */
public class LinkedHashSet<E> extends HashSet<E> implements Set<E>, Cloneable, java.io.Serializable {

    private static final long serialVersionUID = -2851667679971038690L;

    /**
     * 默认⽆参构造函数
     */
    public LinkedHashSet() {
        super(16, .75f, true);
    }

    /**
     * 指定初始容量
     * @param initialCapacity
     */
    public LinkedHashSet(int initialCapacity) {
        super(initialCapacity, .75f, true);
    }

    /**
     * 指定初始容量和加载因⼦
     * @param initialCapacity
     * @param loadFactor
     */
    public LinkedHashSet(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor, true);
    }

    /**
     * 构造包含指定集合的元素
     * @param c
     */
    public LinkedHashSet(Collection<? extends E> c) {
        // 前⾯两个参数分别设置HashMap 的初始容量和加载因⼦。dummy 可以忽略掉，这个参数只是为了区分 HashSet 别的构造⽅法。
        super(Math.max(2 * c.size(), 11), .75f, true);
        addAll(c);
    }

    @Override
    public Spliterator<E> spliterator() {
        return Spliterators.spliterator(this, Spliterator.DISTINCT | Spliterator.ORDERED);
    }
}
