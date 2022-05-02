/*
 * Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

/**
 * 这是⼀个标记接⼝，⼀般此标记接⼝⽤于 List 实现，以表明它们⽀持快速（通常是恒定时间）的随机访问。
 * 该接⼝的主要⽬的是允许通⽤算法改变其⾏为，以便在应⽤于随机或顺序访问列表时提供良好的性能。
 * ⽐如在⼯具类 Collections(这个⼯具类后⾯会详细讲解)中，应⽤⼆分查找⽅法时判断是否实现了RandomAccess 接⼝
 * @author liuzhen
 * @date 2022/4/24 17:04
 */
public interface RandomAccess {
}
