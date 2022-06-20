/*
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

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

public interface RejectedExecutionHandler {

    /**
     * 拒绝
     * @date 2022/6/20 22:22
     * @param r
     * @param executor
     * @return void
     */
    void rejectedExecution(Runnable r, ThreadPoolExecutor executor);
}
