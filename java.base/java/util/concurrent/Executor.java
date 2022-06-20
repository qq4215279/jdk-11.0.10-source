/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent;

/**
 *
 * @date 2022/6/20 20:26
 */
public interface Executor {

    /**
     * 任务提交
     * @date 2022/6/20 22:03
     * @param command
     * @return void
     */
    void execute(Runnable command);
}
