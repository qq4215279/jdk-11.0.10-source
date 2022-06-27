/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;

/**
 *
 * @date 2022/6/21 22:26
 */
public class ForkJoinWorkerThread extends Thread {

    /** 当前工作线程所在的线程池，反向引用 */
    final ForkJoinPool pool; // the pool this thread works in
    /** 工作队列 */
    final ForkJoinPool.WorkQueue workQueue; // work-stealing mechanics

    private static final AccessControlContext INNOCUOUS_ACC =
        new AccessControlContext(new ProtectionDomain[] {new ProtectionDomain(null, null)});

    protected ForkJoinWorkerThread(ForkJoinPool pool) {
        // Use a placeholder until a useful name can be set in registerWorker
        super("aForkJoinWorkerThread");
        this.pool = pool;
        this.workQueue = pool.registerWorker(this);
    }

    ForkJoinWorkerThread(ForkJoinPool pool, ClassLoader ccl) {
        super("aForkJoinWorkerThread");
        super.setContextClassLoader(ccl);
        ThreadLocalRandom.setInheritedAccessControlContext(this, INNOCUOUS_ACC);
        this.pool = pool;
        this.workQueue = pool.registerWorker(this);
    }

    ForkJoinWorkerThread(ForkJoinPool pool, ClassLoader ccl, ThreadGroup threadGroup, AccessControlContext acc) {
        super(threadGroup, null, "aForkJoinWorkerThread");
        super.setContextClassLoader(ccl);
        ThreadLocalRandom.setInheritedAccessControlContext(this, acc);
        ThreadLocalRandom.eraseThreadLocals(this); // clear before registering
        this.pool = pool;
        this.workQueue = pool.registerWorker(this);
    }

    public ForkJoinPool getPool() {
        return pool;
    }

    public int getPoolIndex() {
        return workQueue.getPoolIndex();
    }

    protected void onStart() {}

    protected void onTermination(Throwable exception) {}

    public void run() {
        if (workQueue.array == null) { // only run once
            Throwable exception = null;
            try {
                onStart();
                // 调用runWorker启动线程
                pool.runWorker(workQueue);
            } catch (Throwable ex) {
                exception = ex;
            } finally {
                try {
                    onTermination(exception);
                } catch (Throwable ex) {
                    if (exception == null)
                        exception = ex;
                } finally {
                    pool.deregisterWorker(this, exception);
                }
            }
        }
    }

    void afterTopLevelExec() {}

    static final class InnocuousForkJoinWorkerThread extends ForkJoinWorkerThread {
        /** The ThreadGroup for all InnocuousForkJoinWorkerThreads */
        private static final ThreadGroup innocuousThreadGroup = AccessController.doPrivileged(new PrivilegedAction<>() {
            public ThreadGroup run() {
                ThreadGroup group = Thread.currentThread().getThreadGroup();
                for (ThreadGroup p; (p = group.getParent()) != null;)
                    group = p;
                return new ThreadGroup(group, "InnocuousForkJoinWorkerThreadGroup");
            }
        });

        InnocuousForkJoinWorkerThread(ForkJoinPool pool) {
            super(pool, ClassLoader.getSystemClassLoader(), innocuousThreadGroup, INNOCUOUS_ACC);
        }

        @Override // to erase ThreadLocals
        void afterTopLevelExec() {
            ThreadLocalRandom.eraseThreadLocals(this);
        }

        @Override // to silently fail
        public void setUncaughtExceptionHandler(UncaughtExceptionHandler x) {}

        @Override // paranoically
        public void setContextClassLoader(ClassLoader cl) {
            throw new SecurityException("setContextClassLoader");
        }
    }
}
