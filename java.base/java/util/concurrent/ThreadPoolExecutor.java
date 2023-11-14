/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @date 2022/6/20 20:25
 */
public class ThreadPoolExecutor extends AbstractExecutorService {
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    private static final int COUNT_BITS = Integer.SIZE - 3;
    private static final int COUNT_MASK = (1 << COUNT_BITS) - 1;

    // runState is stored in the high-order bits
    private static final int RUNNING = -1 << COUNT_BITS;
    private static final int SHUTDOWN = 0 << COUNT_BITS;
    private static final int STOP = 1 << COUNT_BITS;
    private static final int TIDYING = 2 << COUNT_BITS;
    private static final int TERMINATED = 3 << COUNT_BITS;


    /** 存放任务的阻塞队列 */
    private final ReentrantLock mainLock = new ReentrantLock();
    /** 线程集合 */
    private final HashSet<Worker> workers = new HashSet<>();


    private final Condition termination = mainLock.newCondition();

    private int largestPoolSize;

    private long completedTaskCount;


    /** 存放任务的阻塞队列 */
    private final BlockingQueue<Runnable> workQueue;
    /** 核心线程数 */
    private volatile int corePoolSize;
    /** 最大线程数 */
    private volatile int maximumPoolSize;
    /** 线程存活时间 */
    private volatile long keepAliveTime;
    private volatile boolean allowCoreThreadTimeOut;
    /** 线程创建工厂 */
    private volatile ThreadFactory threadFactory;
    /** 拒绝处理 */
    private volatile RejectedExecutionHandler handler;
    /** 默认拒绝策略 */
    private static final RejectedExecutionHandler defaultHandler = new AbortPolicy();

    private static final RuntimePermission shutdownPerm = new RuntimePermission("modifyThread");

    /**
     * Worker继承于AQS，也就是说Worker本身就是一把锁。这把锁有什么用处呢？ 好处：用于线程池的关闭、线程执行任务的过程中。
     * @date 2022/6/20 20:32
     */
    private final class Worker extends AbstractQueuedSynchronizer implements Runnable {
        private static final long serialVersionUID = 6138294804551838833L;

        /** Worker封装的线程 -- 当前Worker对象封装的线程 */
        final Thread thread;
        /** Worker接收到的第1个任务 -- 线程需要运行的第一个任务。可以是null，如果是null，则线程从队列获取任务 */
        Runnable firstTask;
        /** Worker执行完毕的任务个数 -- 记录线程执行完成的任务数量，每个线程一个计数器 */
        volatile long completedTasks;

        // TODO: switch to AbstractQueuedLongSynchronizer and move

        /** 
         * 使用给定的第一个任务并利用线程工厂创建Worker实例
         * @date 2022/6/20 22:12
         * @param firstTask 线程的第一个任务，如果没有，就设置为null，此时线程会从队列 获取任务。
         * @return 
         */
        Worker(Runnable firstTask) {
            // 线程处于阻塞状态，调用runWorker的时候中断
            setState(-1);
            this.firstTask = firstTask;
            this.thread = getThreadFactory().newThread(this);
        }

        /**
         * 调用ThreadPoolExecutor的runWorker方法执行线程的运行
         * @date 2022/6/20 22:12
         * @param
         * @return void
         */
        public void run() {
            runWorker(this);
        }

        protected boolean isHeldExclusively() {
            return getState() != 0;
        }

        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public void lock() {
            acquire(1);
        }

        public boolean tryLock() {
            return tryAcquire(1);
        }

        public void unlock() {
            release(1);
        }

        public boolean isLocked() {
            return isHeldExclusively();
        }

        /** 
         *
         * @date 2022/6/20 22:00 
         * @param  
         * @return void
         */
        void interruptIfStarted() {
            Thread t;
            // 只要启动了，并且没有中断，则一律中断
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }

    // Public constructors and methods

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
        BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, Executors.defaultThreadFactory(),
            defaultHandler);
    }

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
        BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, defaultHandler);
    }

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
        BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, Executors.defaultThreadFactory(), handler);
    }

    /**
     * 构造方法
     * @date 2022/6/20 20:41
     * @param corePoolSize 在线程池中始终维护的核心线程个数
     * @param maximumPoolSize 在corePooSize已满、队列也满的情况下，扩充线程至此值。
     * @param keepAliveTime maxPoolSize中的空闲线程，销毁所需要的时间，总线程数收缩回corePoolSize。
     * @param unit maxPoolSize中的空闲线程，销毁所需要的时间，总线程数收缩回corePoolSize。
     * @param workQueue 线程池所用的队列类型。
     * @param threadFactory 线程创建工厂，可以自定义，有默认值
     * @param handler corePoolSize已满，队列已满，maxPoolSize 已满，最后的拒绝策略。
     * @return
     */
    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
        BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        if (corePoolSize < 0 || maximumPoolSize <= 0 || maximumPoolSize < corePoolSize || keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }

    // Packing and unpacking ctl
    private static int runStateOf(int c) {
        return c & ~COUNT_MASK;
    }

    private static int workerCountOf(int c) {
        return c & COUNT_MASK;
    }

    private static int ctlOf(int rs, int wc) {
        return rs | wc;
    }

    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }

    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }

    private static boolean isRunning(int c) {
        return c < SHUTDOWN;
    }

    private boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }

    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }

    private void decrementWorkerCount() {
        ctl.addAndGet(-1);
    }

    private void advanceRunState(int targetState) {
        // assert targetState == SHUTDOWN || targetState == STOP;
        for (;;) {
            int c = ctl.get();
            if (runStateAtLeast(c, targetState) || ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c))))
                break;
        }
    }

    /** 
     * 尝试终止
     * tryTerminate()不会强行终止线程池，只是做了一下检测：当workerCount为0，workerQueue为空时，先把状态切换到TIDYING，然后调用钩子方法terminated()。
     * 当钩子方法执行完成时，把状态从TIDYING 改为 TERMINATED，接着调用termination.singleAll()，通知前面阻塞在awaitTermination的所有调用者线程。
     * 所以，TIDYING和TREMINATED的区别是在二者之间执行了一个钩子方法terminated()，目前是一个空实现。
     * @date 2022/6/20 22:02 
     * @param  
     * @return void
     */
    final void tryTerminate() {
        for (;;) {
            int c = ctl.get();
            if (isRunning(c) || runStateAtLeast(c, TIDYING) || (runStateLessThan(c, STOP) && !workQueue.isEmpty()))
                return;
            if (workerCountOf(c) != 0) { // Eligible to terminate
                interruptIdleWorkers(ONLY_ONE);
                return;
            }

            // 当workQueue为空，wordCount为0时，执行下述代码。
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                // 将状态切换到到TIDYING状态
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        // 调用钩子函数
                        terminated();
                    } finally {
                        // 将状态由TIDYING改为 TERMINATED
                        ctl.set(ctlOf(TERMINATED, 0));
                        // 通知awaitTermination(...)
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }
            // else retry on failed CAS
        }
    }

    private void checkShutdownAccess() {
        // assert mainLock.isHeldByCurrentThread();
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(shutdownPerm);
            for (Worker w : workers)
                security.checkAccess(w.thread);
        }
    }

    /** 
     *
     * @date 2022/6/20 22:00 
     * @param  
     * @return void
     */
    private void interruptWorkers() {
        // assert mainLock.isHeldByCurrentThread();
        for (Worker w : workers)
            w.interruptIfStarted();
    }

    /** 
     *
     * @date 2022/6/20 21:50
     * @param  
     * @return void
     */
    private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }

    /** 
     * 关键区别点在tryLock()：
     * 一个线程在执行一个任务之前，会先加锁，这意味着通过是否持有锁，可以判断出线程是否处于空闲状态。
     * tryLock()如果调用成功，说明线程处于空闲状态，向其发送中断信号；否则不发送。
     * @date 2022/6/20 21:50 
     * @param onlyOne 
     * @return void
     */
    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                // 如果tryLock 成功，表示线程处于空闲状态；如果不成功，表示线程持有锁，正在执行某个任务
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                if (onlyOne)
                    break;
            }
        } finally {
            mainLock.unlock();
        }
    }

    private static final boolean ONLY_ONE = true;

    /**
     * 拒绝策略
     * @date 2022/6/20 22:21
     * @param command
     * @return void
     */
    final void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    void onShutdown() {}

    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        ArrayList<Runnable> taskList = new ArrayList<>();
        q.drainTo(taskList);
        if (!q.isEmpty()) {
            for (Runnable r : q.toArray(new Runnable[0])) {
                if (q.remove(r))
                    taskList.add(r);
            }
        }
        return taskList;
    }

    /**
     * 当一个Worker最终退出的时候，会执行清理工作
     * @date 2022/6/20 22:18
     * @param w
     * @param completedAbruptly
     * @return void
     */
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        // 如果线程正常退出，不会执行if的语句，这里一般是非正常退出，需要将worker数量减一
        if (completedAbruptly) // If abrupt, then workerCount wasn't adjusted
            decrementWorkerCount();

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            completedTaskCount += w.completedTasks;
            // 将自己的worker从集合移除
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }

        // 每个线程在结束的时候都会调用该方法，看是否可以停止线程池
        tryTerminate();

        int c = ctl.get();
        // 如果在线程退出前，发现线程池还没有关闭
        if (runStateLessThan(c, STOP)) {
            if (!completedAbruptly) {
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                // 如果线程池中没有其他线程了，并且任务队列非空
                if (min == 0 && !workQueue.isEmpty())
                    min = 1;
                // 如果工作线程数大于min，表示队列中的任务可以由其他线程执行，退出当前线程
                if (workerCountOf(c) >= min)
                    return; // replacement not needed
            }

            // 如果当前线程退出前发现线程池没有结束，任务队列不是空的，也没有其他线程来执行。就再启动一个线程来处理。
            addWorker(null, false);
        }
    }

    /**
     *
     * @date 2022/6/20 22:16
     * @param
     * @return java.lang.Runnable
     */
    private Runnable getTask() {
        boolean timedOut = false; // Did the last poll() time out?

        for (;;) {
            int c = ctl.get();

            // 如果线程池调用了shutdownNow()，返回null；如果线程池调用了shutdown()，并且任务队列为空，也返回null
            if (runStateAtLeast(c, SHUTDOWN) && (runStateAtLeast(c, STOP) || workQueue.isEmpty())) {
                // 工作线程数减一
                decrementWorkerCount();
                return null;
            }

            int wc = workerCountOf(c);

            // Are workers subject to culling?
            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;

            if ((wc > maximumPoolSize || (timed && timedOut)) && (wc > 1 || workQueue.isEmpty())) {
                if (compareAndDecrementWorkerCount(c))
                    return null;
                continue;
            }

            try {
                // 如果队列为空，就会阻塞pool或者take，前者有超时时间，后者没有超时时间。 一旦中断，此处抛异常，对应上文场景1。
                Runnable r = timed ? workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) : workQueue.take();
                if (r != null)
                    return r;
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }

    /**
     *
     * @date 2022/6/20 22:13
     * @param w
     * @return void
     */
    final void runWorker(Worker w) {
        Thread wt = Thread.currentThread();
        Runnable task = w.firstTask;
        w.firstTask = null;
        // 中断Worker封装的线程
        w.unlock(); // allow interrupts
        boolean completedAbruptly = true;
        try {
            // 如果线程初始任务不是null，或者从队列获取的任务不是null，表示该线程应该执行任务。
            while (task != null || (task = getTask()) != null) {
                // 获取线程锁
                w.lock();
                // 如果线程池停止了，确保线程被中断；如果线程池正在运行，确保线程不被中断
                if ((runStateAtLeast(ctl.get(), STOP) || (Thread.interrupted() && runStateAtLeast(ctl.get(), STOP)))
                    && !wt.isInterrupted())
                    // 获取到任务后，再次检查线程池状态，如果发现线程池已经停止，则给自己发中断信号
                    wt.interrupt();
                try {
                    // 任务执行之前的钩子方法，实现为空
                    beforeExecute(wt, task);
                    try {
                        task.run();
                        // 任务执行结束后的钩子方法，实现为空
                        afterExecute(task, null);
                    } catch (Throwable ex) {
                        afterExecute(task, ex);
                        throw ex;
                    }
                } finally {
                    // 任务执行完成，将task设置为null
                    task = null;
                    // 线程已完成的任务数加1
                    w.completedTasks++;
                    // 释放线程锁
                    w.unlock();
                }
            }
            // 判断线程是否是正常退出
            completedAbruptly = false;
        } finally {
            // Worker退出
            processWorkerExit(w, completedAbruptly);
        }
    }

    /**
     * 任务提交
     * @date 2022/6/20 22:04
     * @param command
     * @return void
     */
    public void execute(Runnable command) {
        if (command == null)
            throw new NullPointerException();

        int c = ctl.get();
        // 如果当前线程数小于corePoolSize，则启动新线程
        if (workerCountOf(c) < corePoolSize) {
            // 添加Worker，并将command设置为Worker线程的第一个任务开始执行。
            if (addWorker(command, true))
                return;
            c = ctl.get();
        }
        // 如果当前的线程数大于或等于corePoolSize，则调用workQueue.offer放入队列
        if (isRunning(c) && workQueue.offer(command)) {
            int recheck = ctl.get();
            // 如果线程池正在停止，则将command任务从队列移除，并拒绝command任务请求。
            if (!isRunning(recheck) && remove(command))
                reject(command);
            else if (workerCountOf(recheck) == 0) // 放入队列中后发现没有线程执行任务，开启新线程
                addWorker(null, false);
        } else if (!addWorker(command, false))
            reject(command);
    }

    /**
     * 该方法用于启动新线程。
     * 如果第二个参数为true，则使用corePoolSize作为上限，否则使用 maxPoolSize作为上限。
     * @date 2022/6/20 22:05
     * @param firstTask
     * @param core
     * @return boolean
     */
    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        for (int c = ctl.get();;) {
            // Check if queue empty only if necessary.
            // 如果线程池状态值起码是SHUTDOWN和STOP，或则第一个任务不是null，或者工作队列为空，则添加worker失败，返回false
            if (runStateAtLeast(c, SHUTDOWN) && (runStateAtLeast(c, STOP) || firstTask != null || workQueue.isEmpty()))
                return false;

            for (;;) {
                // 工作线程数达到上限，要么是corePoolSize要么是maximumPoolSize，启动线程失败
                if (workerCountOf(c) >= ((core ? corePoolSize : maximumPoolSize) & COUNT_MASK))
                    return false;
                // 增加worker数量成功，返回到retry语句
                if (compareAndIncrementWorkerCount(c))
                    break retry;
                c = ctl.get(); // Re-read ctl
                // 如果线程池运行状态起码是SHUTDOWN，则重试retry标签语句，CAS
                if (runStateAtLeast(c, SHUTDOWN))
                    continue retry;
                // else CAS failed due to workerCount change; retry inner loop
            }
        }

        // worker数量加1成功后，接着运行：
        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            // 新建worker对象
            w = new Worker(firstTask);
            // 获取线程对象
            final Thread t = w.thread;
            if (t != null) {
                final ReentrantLock mainLock = this.mainLock;
                // 加锁
                mainLock.lock();
                try {
                    int c = ctl.get();

                    if (isRunning(c) || (runStateLessThan(c, STOP) && firstTask == null)) {
                        // 由于线程已经在运行中，无法启动，抛异常
                        if (t.getState() != Thread.State.NEW)
                            throw new IllegalThreadStateException();
                        // 将线程对应的worker加入worker集合
                        workers.add(w);
                        workerAdded = true;
                        int s = workers.size();
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                    }
                } finally {
                    // 释放锁
                    mainLock.unlock();
                }

                // 如果添加worker成功，则启动该worker对应的线程
                if (workerAdded) {
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            // 如果启动新线程失败
            if (!workerStarted)
                // workCount - 1
                addWorkerFailed(w);
        }
        return workerStarted;
    }

    private void addWorkerFailed(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (w != null)
                workers.remove(w);
            decrementWorkerCount();
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 关闭线程池
     * @date 2022/6/20 21:41
     * @param
     * @return void
     */
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        // 加锁，确保线程安全
        mainLock.lock();
        try {
            // 检查是否有关闭线程池的权限
            checkShutdownAccess();
            // 将线程状态修改为SHUTDOWN
            advanceRunState(SHUTDOWN);
            // 中断空闲的线程 重点！
            interruptIdleWorkers();
            // 具体空方法体的狗子方法
            onShutdown(); // hook for ScheduledThreadPoolExecutor
        } finally {
            mainLock.unlock();
        }
        
        tryTerminate();
    }

    /**
     * 立马关闭线程池
     * @date 2022/6/20 21:48
     * @param
     * @return java.util.List<java.lang.Runnable>
     */
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        // 加锁，确保线程安全
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 检查是否有关闭线程池的权限
            checkShutdownAccess();
            // 将线程状态修改为STOP
            advanceRunState(STOP);
            // 中断所有的线程 重点！
            interruptWorkers();
            // 任务队列清空
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }
        
        tryTerminate();
        return tasks;
    }

    /**
     * 等待关闭线程池
     * @date 2022/6/20 21:42
     * @param timeout
     * @param unit
     * @return boolean
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 判断线程池状态，是否为TERMINATED
            while (runStateLessThan(ctl.get(), TERMINATED)) {
                if (nanos <= 0L)
                    return false;
                nanos = termination.awaitNanos(nanos);
            }
            return true;
        } finally {
            mainLock.unlock();
        }
    }

    public boolean isShutdown() {
        return runStateAtLeast(ctl.get(), SHUTDOWN);
    }

    boolean isStopped() {
        return runStateAtLeast(ctl.get(), STOP);
    }

    public boolean isTerminating() {
        int c = ctl.get();
        return runStateAtLeast(c, SHUTDOWN) && runStateLessThan(c, TERMINATED);
    }

    public boolean isTerminated() {
        return runStateAtLeast(ctl.get(), TERMINATED);
    }

    @Deprecated(since = "9")
    protected void finalize() {}

    public void setThreadFactory(ThreadFactory threadFactory) {
        if (threadFactory == null)
            throw new NullPointerException();
        this.threadFactory = threadFactory;
    }

    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler == null)
            throw new NullPointerException();
        this.handler = handler;
    }

    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handler;
    }

    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException();
        int delta = corePoolSize - this.corePoolSize;
        this.corePoolSize = corePoolSize;
        if (workerCountOf(ctl.get()) > corePoolSize)
            interruptIdleWorkers();
        else if (delta > 0) {
            int k = Math.min(delta, workQueue.size());
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty())
                    break;
            }
        }
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public boolean prestartCoreThread() {
        return workerCountOf(ctl.get()) < corePoolSize && addWorker(null, true);
    }

    /**
     *
     * @date 2022/6/20 23:05
     * @param
     * @return void
     */
    void ensurePrestart() {
        // 如果wc小于corePoolSize，则addWork，开新线程，否则什么也不做
        int wc = workerCountOf(ctl.get());
        if (wc < corePoolSize)
            addWorker(null, true);
        else if (wc == 0)
            addWorker(null, false);
    }

    public int prestartAllCoreThreads() {
        int n = 0;
        while (addWorker(null, true))
            ++n;
        return n;
    }

    public boolean allowsCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    public void allowCoreThreadTimeOut(boolean value) {
        if (value && keepAliveTime <= 0)
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            if (value)
                interruptIdleWorkers();
        }
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException();
        this.maximumPoolSize = maximumPoolSize;
        if (workerCountOf(ctl.get()) > maximumPoolSize)
            interruptIdleWorkers();
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setKeepAliveTime(long time, TimeUnit unit) {
        if (time < 0)
            throw new IllegalArgumentException();
        if (time == 0 && allowsCoreThreadTimeOut())
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        long keepAliveTime = unit.toNanos(time);
        long delta = keepAliveTime - this.keepAliveTime;
        this.keepAliveTime = keepAliveTime;
        if (delta < 0)
            interruptIdleWorkers();
    }

    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS);
    }

    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }

    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        tryTerminate(); // In case SHUTDOWN and now empty
        return removed;
    }

    public void purge() {
        final BlockingQueue<Runnable> q = workQueue;
        try {
            Iterator<Runnable> it = q.iterator();
            while (it.hasNext()) {
                Runnable r = it.next();
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    it.remove();
            }
        } catch (ConcurrentModificationException fallThrough) {
            for (Object r : q.toArray())
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    q.remove(r);
        }

        tryTerminate(); // In case SHUTDOWN and now empty
    }

    public int getPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return runStateAtLeast(ctl.get(), TIDYING) ? 0 : workers.size();
        } finally {
            mainLock.unlock();
        }
    }

    public int getActiveCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int n = 0;
            for (Worker w : workers)
                if (w.isLocked())
                    ++n;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    public int getLargestPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return largestPoolSize;
        } finally {
            mainLock.unlock();
        }
    }

    public long getTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
                if (w.isLocked())
                    ++n;
            }
            return n + workQueue.size();
        } finally {
            mainLock.unlock();
        }
    }

    public long getCompletedTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers)
                n += w.completedTasks;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    public String toString() {
        long ncompleted;
        int nworkers, nactive;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            ncompleted = completedTaskCount;
            nactive = 0;
            nworkers = workers.size();
            for (Worker w : workers) {
                ncompleted += w.completedTasks;
                if (w.isLocked())
                    ++nactive;
            }
        } finally {
            mainLock.unlock();
        }
        int c = ctl.get();
        String runState = isRunning(c) ? "Running" : runStateAtLeast(c, TERMINATED) ? "Terminated" : "Shutting down";
        return super.toString() + "[" + runState + ", pool size = " + nworkers + ", active threads = " + nactive
            + ", queued tasks = " + workQueue.size() + ", completed tasks = " + ncompleted + "]";
    }

    protected void beforeExecute(Thread t, Runnable r) {}

    protected void afterExecute(Runnable r, Throwable t) {}

    protected void terminated() {}

    /** 
     * 拒绝策略1：调用者直接在自己的线程里执行，线程池不处理，比如到医院打点滴，医院没地方了，到你家自己操作吧。
     * @date 2022/6/20 22:24
     */
    public static class CallerRunsPolicy implements RejectedExecutionHandler {
        public CallerRunsPolicy() {}

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }

    /**
     * 拒绝策略2（默认策略）：线程池抛异常。
     * @date 2022/6/20 22:25
     */
    public static class AbortPolicy implements RejectedExecutionHandler {
        public AbortPolicy() {}

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() + " rejected from " + e.toString());
        }
    }

    /** 
     * 拒绝策略3：线程池直接丢掉任务，神不知鬼不觉。
     * @date 2022/6/20 22:26 
     */
    public static class DiscardPolicy implements RejectedExecutionHandler {
        public DiscardPolicy() {}

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {}
    }

    /** 
     * 拒绝策略4：删除队列中最早的任务，将当前任务入队列。
     * @date 2022/6/20 22:26
     */
    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        public DiscardOldestPolicy() {}

        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
}
