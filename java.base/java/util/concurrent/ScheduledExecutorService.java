/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent;

public interface ScheduledExecutorService extends ExecutorService {

    /** 
     * 延迟执行任务
     * @date 2022/6/20 22:43 
     * @param command
     * @param delay
     * @param unit 
     * @return java.util.concurrent.ScheduledFuture<?>
     */
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    /**
     * 周期执行任务
     * @date 2022/6/20 22:43
     * @param command
     * @param initialDelay
     * @param period
     * @param unit
     * @return java.util.concurrent.ScheduledFuture<?>
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);

}
