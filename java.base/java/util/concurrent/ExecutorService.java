/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent;

import java.util.Collection;
import java.util.List;

/**
 *
 * @date 2022/6/20 20:26
 */
public interface ExecutorService extends Executor {

    /**
     * 关闭线程池
     * @date 2022/6/20 21:41
     * @param
     * @return void
     */
    void shutdown();

    /**
     *
     * @date 2022/6/20 21:48
     * @param
     * @return java.util.List<java.lang.Runnable>
     */
    List<Runnable> shutdownNow();

    boolean isShutdown();

    boolean isTerminated();

    /**
     * 等待关闭线程池
     * @date 2022/6/20 21:42
     * @param timeout
     * @param unit
     * @return boolean
     */
    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Callable任务提交
     * @date 2022/9/9 7:24
     * @param task
     * @return java.util.concurrent.Future<T>
     */
    <T> Future<T> submit(Callable<T> task);

    <T> Future<T> submit(Runnable task, T result);

    Future<?> submit(Runnable task);

    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException;

    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException;

    <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException;

    <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException;
}
