/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent;

/**
 *
 * @date 2022/6/20 23:17
 */
public interface Future<V> {

    boolean cancel(boolean mayInterruptIfRunning);

    boolean isCancelled();

    boolean isDone();

    /**
     * 调用get()方法会阻塞在那，直到结果返回。
     * @date 2022/6/20 23:17
     * @param
     * @return V
     */
    V get() throws InterruptedException, ExecutionException;

    V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;
}
