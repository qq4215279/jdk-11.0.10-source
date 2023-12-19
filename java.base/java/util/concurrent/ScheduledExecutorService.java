/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent;

/**
 * 周期性线程池接口
 * @date 2023/12/19 14:49
 */
public interface ScheduledExecutorService extends ExecutorService {

    /** 
     * 延迟执行任务
     * @date 2022/6/20 22:43 
     * @param command 命令执行的任务
     * @param delay 延迟执行的时间
     * @param unit 延迟执行时间单位
     * @return java.util.concurrent.ScheduledFuture<?>
     */
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    /**
     * 周期执行任务 - 以固定周期频率执行任务
     * @date 2022/6/20 22:43
     * @param command 任务
     * @param initialDelay 初始化延时时间
     * @param period 两次任务开始执行最小间隔时间
     * @param unit 延迟执行时间单位
     * @return java.util.concurrent.ScheduledFuture<?>
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    /**
     * 周期执行任务 - 按照指定频率间隔执行某个任务。
     * @param command 执行线程
     * @param initialDelay 初始化延时时间
     * @param delay 前一次任务执行结束到下一次任务执行开始的间隔时间
     * @param unit 计时单位
     * 如果要执行的任务时间大于间隔时间，会如何处理呢？
     * 其实当执行任务的时间大于我们指定的间隔时间时，它并不会在指定间隔时开辟一个新的线程并发执行这个任务，而是等待该线程执行完毕。
     * @return java.util.concurrent.ScheduledFuture<?>
     * @date 2023/12/19 14:33
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);

}
