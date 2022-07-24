/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */
package java.util.function;

/**
 *
 * @date 2022/7/24 15:36
 */
@FunctionalInterface
public interface LongBinaryOperator {

    /**
     *
     * @date 2022/7/24 15:36
     * @param left 操作符的左值，就是base变量或者Cells[]中元素的当前值；
     * @param right 右值，就是add()方法传入的参数x。
     * @return long
     */
    long applyAsLong(long left, long right);
}
