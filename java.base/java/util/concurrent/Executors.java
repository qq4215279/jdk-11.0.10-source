/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent;

import static java.lang.ref.Reference.reachabilityFence;
import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import sun.security.util.SecurityConstants;

public class Executors {

    /**
     * 2. 固定数目线程的线程池：
     * @date 2022/6/20 22:36
     * @param nThreads
     * @return java.util.concurrent.ExecutorService
     */
    public static ExecutorService newFixedThreadPool(int nThreads) {
        return new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());
    }

    public static ExecutorService newFixedThreadPool(int nThreads, ThreadFactory threadFactory) {
        return new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS,
                                      new LinkedBlockingQueue<Runnable>(), threadFactory);
    }

    public static ExecutorService newWorkStealingPool(int parallelism) {
        return new ForkJoinPool(parallelism, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);
    }

    public static ExecutorService newWorkStealingPool() {
        return new ForkJoinPool(Runtime.getRuntime().availableProcessors(),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);
    }

    /**
     * 1. 单线程的线程池
     * @date 2022/6/20 22:35
     * @param
     * @return java.util.concurrent.ExecutorService
     */
    public static ExecutorService newSingleThreadExecutor() {
        return new FinalizableDelegatedExecutorService(
            new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>()));
    }

    public static ExecutorService newSingleThreadExecutor(ThreadFactory threadFactory) {
        return new FinalizableDelegatedExecutorService(new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(), threadFactory));
    }

    /**
     * 每接收一个请求，就创建一个线程来执行：
     * @date 2022/6/20 22:37
     * @param
     * @return java.util.concurrent.ExecutorService
     */
    public static ExecutorService newCachedThreadPool() {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
    }

    public static ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
            threadFactory);
    }

    /**
     * 单线程具有周期调度功能的线程池：
     * @date 2022/6/20 22:37
     * @param
     * @return java.util.concurrent.ScheduledExecutorService
     */
    public static ScheduledExecutorService newSingleThreadScheduledExecutor() {
        return new DelegatedScheduledExecutorService(new ScheduledThreadPoolExecutor(1));
    }

    public static ScheduledExecutorService newSingleThreadScheduledExecutor(ThreadFactory threadFactory) {
        return new DelegatedScheduledExecutorService(new ScheduledThreadPoolExecutor(1, threadFactory));
    }

    /**
     * 多线程，有调度功能的线程池：
     * @date 2022/6/20 22:38
     * @param corePoolSize
     * @return java.util.concurrent.ScheduledExecutorService
     */
    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize) {
        return new ScheduledThreadPoolExecutor(corePoolSize);
    }

    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize, ThreadFactory threadFactory) {
        return new ScheduledThreadPoolExecutor(corePoolSize, threadFactory);
    }

    public static ExecutorService unconfigurableExecutorService(ExecutorService executor) {
        if (executor == null)
            throw new NullPointerException();
        return new DelegatedExecutorService(executor);
    }

    public static ScheduledExecutorService unconfigurableScheduledExecutorService(ScheduledExecutorService executor) {
        if (executor == null)
            throw new NullPointerException();
        return new DelegatedScheduledExecutorService(executor);
    }

    public static ThreadFactory defaultThreadFactory() {
        return new DefaultThreadFactory();
    }

    public static ThreadFactory privilegedThreadFactory() {
        return new PrivilegedThreadFactory();
    }

    public static <T> Callable<T> callable(Runnable task, T result) {
        if (task == null)
            throw new NullPointerException();
        return new RunnableAdapter<T>(task, result);
    }

    public static Callable<Object> callable(Runnable task) {
        if (task == null)
            throw new NullPointerException();
        return new RunnableAdapter<Object>(task, null);
    }

    public static Callable<Object> callable(final PrivilegedAction<?> action) {
        if (action == null)
            throw new NullPointerException();
        return new Callable<Object>() {
            public Object call() {
                return action.run();
            }
        };
    }

    public static Callable<Object> callable(final PrivilegedExceptionAction<?> action) {
        if (action == null)
            throw new NullPointerException();
        return new Callable<Object>() {
            public Object call() throws Exception {
                return action.run();
            }
        };
    }

    public static <T> Callable<T> privilegedCallable(Callable<T> callable) {
        if (callable == null)
            throw new NullPointerException();
        return new PrivilegedCallable<T>(callable);
    }

    public static <T> Callable<T> privilegedCallableUsingCurrentClassLoader(Callable<T> callable) {
        if (callable == null)
            throw new NullPointerException();
        return new PrivilegedCallableUsingCurrentClassLoader<T>(callable);
    }

    // Non-public classes supporting the public methods

    private static final class RunnableAdapter<T> implements Callable<T> {
        private final Runnable task;
        private final T result;

        RunnableAdapter(Runnable task, T result) {
            this.task = task;
            this.result = result;
        }

        public T call() {
            task.run();
            return result;
        }

        public String toString() {
            return super.toString() + "[Wrapped task = " + task + "]";
        }
    }

    private static final class PrivilegedCallable<T> implements Callable<T> {
        final Callable<T> task;
        final AccessControlContext acc;

        PrivilegedCallable(Callable<T> task) {
            this.task = task;
            this.acc = AccessController.getContext();
        }

        public T call() throws Exception {
            try {
                return AccessController.doPrivileged(new PrivilegedExceptionAction<T>() {
                    public T run() throws Exception {
                        return task.call();
                    }
                }, acc);
            } catch (PrivilegedActionException e) {
                throw e.getException();
            }
        }

        public String toString() {
            return super.toString() + "[Wrapped task = " + task + "]";
        }
    }

    private static final class PrivilegedCallableUsingCurrentClassLoader<T> implements Callable<T> {
        final Callable<T> task;
        final AccessControlContext acc;
        final ClassLoader ccl;

        PrivilegedCallableUsingCurrentClassLoader(Callable<T> task) {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                // Calls to getContextClassLoader from this class
                // never trigger a security check, but we check
                // whether our callers have this permission anyways.
                sm.checkPermission(SecurityConstants.GET_CLASSLOADER_PERMISSION);

                // Whether setContextClassLoader turns out to be necessary
                // or not, we fail fast if permission is not available.
                sm.checkPermission(new RuntimePermission("setContextClassLoader"));
            }
            this.task = task;
            this.acc = AccessController.getContext();
            this.ccl = Thread.currentThread().getContextClassLoader();
        }

        public T call() throws Exception {
            try {
                return AccessController.doPrivileged(new PrivilegedExceptionAction<T>() {
                    public T run() throws Exception {
                        Thread t = Thread.currentThread();
                        ClassLoader cl = t.getContextClassLoader();
                        if (ccl == cl) {
                            return task.call();
                        } else {
                            t.setContextClassLoader(ccl);
                            try {
                                return task.call();
                            } finally {
                                t.setContextClassLoader(cl);
                            }
                        }
                    }
                }, acc);
            } catch (PrivilegedActionException e) {
                throw e.getException();
            }
        }

        public String toString() {
            return super.toString() + "[Wrapped task = " + task + "]";
        }
    }

    private static class DefaultThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        DefaultThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            namePrefix = "pool-" + poolNumber.getAndIncrement() + "-thread-";
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    private static class PrivilegedThreadFactory extends DefaultThreadFactory {
        final AccessControlContext acc;
        final ClassLoader ccl;

        PrivilegedThreadFactory() {
            super();
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                // Calls to getContextClassLoader from this class
                // never trigger a security check, but we check
                // whether our callers have this permission anyways.
                sm.checkPermission(SecurityConstants.GET_CLASSLOADER_PERMISSION);

                // Fail fast
                sm.checkPermission(new RuntimePermission("setContextClassLoader"));
            }
            this.acc = AccessController.getContext();
            this.ccl = Thread.currentThread().getContextClassLoader();
        }

        public Thread newThread(final Runnable r) {
            return super.newThread(new Runnable() {
                public void run() {
                    AccessController.doPrivileged(new PrivilegedAction<>() {
                        public Void run() {
                            Thread.currentThread().setContextClassLoader(ccl);
                            r.run();
                            return null;
                        }
                    }, acc);
                }
            });
        }
    }

    private static class DelegatedExecutorService implements ExecutorService {
        private final ExecutorService e;

        DelegatedExecutorService(ExecutorService executor) {
            e = executor;
        }

        public void execute(Runnable command) {
            try {
                e.execute(command);
            } finally {
                reachabilityFence(this);
            }
        }

        public void shutdown() {
            e.shutdown();
        }

        public List<Runnable> shutdownNow() {
            try {
                return e.shutdownNow();
            } finally {
                reachabilityFence(this);
            }
        }

        public boolean isShutdown() {
            try {
                return e.isShutdown();
            } finally {
                reachabilityFence(this);
            }
        }

        public boolean isTerminated() {
            try {
                return e.isTerminated();
            } finally {
                reachabilityFence(this);
            }
        }

        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            try {
                return e.awaitTermination(timeout, unit);
            } finally {
                reachabilityFence(this);
            }
        }

        public Future<?> submit(Runnable task) {
            try {
                return e.submit(task);
            } finally {
                reachabilityFence(this);
            }
        }

        public <T> Future<T> submit(Callable<T> task) {
            try {
                return e.submit(task);
            } finally {
                reachabilityFence(this);
            }
        }

        public <T> Future<T> submit(Runnable task, T result) {
            try {
                return e.submit(task, result);
            } finally {
                reachabilityFence(this);
            }
        }

        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
            try {
                return e.invokeAll(tasks);
            } finally {
                reachabilityFence(this);
            }
        }

        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
            try {
                return e.invokeAll(tasks, timeout, unit);
            } finally {
                reachabilityFence(this);
            }
        }

        public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
            try {
                return e.invokeAny(tasks);
            } finally {
                reachabilityFence(this);
            }
        }

        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
            try {
                return e.invokeAny(tasks, timeout, unit);
            } finally {
                reachabilityFence(this);
            }
        }
    }

    private static class FinalizableDelegatedExecutorService extends DelegatedExecutorService {
        FinalizableDelegatedExecutorService(ExecutorService executor) {
            super(executor);
        }

        @SuppressWarnings("deprecation")
        protected void finalize() {
            super.shutdown();
        }
    }

    private static class DelegatedScheduledExecutorService extends DelegatedExecutorService
        implements ScheduledExecutorService {
        private final ScheduledExecutorService e;

        DelegatedScheduledExecutorService(ScheduledExecutorService executor) {
            super(executor);
            e = executor;
        }

        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            return e.schedule(command, delay, unit);
        }

        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            return e.schedule(callable, delay, unit);
        }

        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            return e.scheduleAtFixedRate(command, initialDelay, period, unit);
        }

        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
            TimeUnit unit) {
            return e.scheduleWithFixedDelay(command, initialDelay, delay, unit);
        }
    }

    private Executors() {}
}
