/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

/**
 * Delayed
 */
public interface Delayed extends Comparable<Delayed> {

    /**
     * 关于该接口：
     * 1. 如果getDelay的返回值小于或等于0，则说明该元素到期，需要从队列中拿出来执行。
     * 2. 该接口首先继承了 Comparable 接口，所以要实现该接口，必须实现 Comparable 接口。具体来说，就是基于getDelay()的返回值比较两个元素的大小。
     * @author liuzhen
     * @date 2022/4/15 23:29
     * @param unit
     * @return long
     */
    long getDelay(TimeUnit unit);
}
