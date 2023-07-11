/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package java.util.function;

import java.util.Objects;

/**
 *
 * @date 2023/7/11 10:57
 */
@FunctionalInterface
public interface Consumer<T> {

    /**
     * 消费
     * @date 2023/7/11 10:57
     * @param t
     * @return void
     */
    void accept(T t);

    /**
     *
     * @date 2023/7/11 10:57
     * @param after
     * @return java.util.function.Consumer<T>
     * 问：accept(t);after.accept(t); 明明是没有返回值的，但是前面为什么可以加return呢？？
     * 答：后来发现return返回的是一个构造Consumer对象的过程
     */
    default Consumer<T> andThen(Consumer<? super T> after) {
        Objects.requireNonNull(after);
        // 等同于如下源码写法
       /* return new Consumer<>() {
            @Override
            public void accept(T t) {
                accept(t);
                after.accept(t);
            }
        };*/

        return (T t) -> {
            accept(t);
            after.accept(t);
        };
    }
}
