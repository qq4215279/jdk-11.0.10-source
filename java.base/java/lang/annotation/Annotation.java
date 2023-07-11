/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package java.lang.annotation;

public interface Annotation {
    boolean equals(Object obj);

    int hashCode();

    String toString();

    /**
     * 返回注解的 Class 对象，即注解接口的 Class 对象。
     * 默认情况下，Java 会为每个注解自动生成一个实现类，该实现类会自动重写 annotationType() 方法并返回注解接口的 Class 对象。
     * 目的是为了提供一个自定义的注解类，并覆盖默认的注解实现类。这在某些特定的应用场景下可能会有用，例如自定义注解处理器或注解相关的框架。对于一般情况下的注解使用，通常不需要手动重写 annotationType() 方法。
     * 注意的是：在一个类实现(implements)注解重写annotationType() api时，返回实现类的Clas对象即可。
     * @date 2023/7/8 15:51
     * @param
     * @return java.lang.Class<? extends java.lang.annotation.Annotation>
     */
    Class<? extends Annotation> annotationType();
}
