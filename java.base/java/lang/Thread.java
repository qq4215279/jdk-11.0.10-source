/*
 * Copyright (c) 1994, 2018, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.lang;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.LockSupport;

import jdk.internal.misc.TerminatingThreadLocal;
import sun.nio.ch.Interruptible;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import sun.security.util.SecurityConstants;
import jdk.internal.HotSpotIntrinsicCandidate;

/**
 *
 * @author liuzhen
 * @date 2022/4/15 16:34
 * @return
 */
public class Thread implements Runnable {
    private static native void registerNatives();

    static {
        registerNatives();
    }

    private volatile String name;
    private int priority;

    private boolean daemon = false;

    private boolean stillborn = false;
    private long eetop;

    private Runnable target;

    private ThreadGroup group;

    private ClassLoader contextClassLoader;

    private AccessControlContext inheritedAccessControlContext;

    private static int threadInitNumber;

    private static synchronized int nextThreadNum() {
        return threadInitNumber++;
    }

    ThreadLocal.ThreadLocalMap threadLocals = null;

    ThreadLocal.ThreadLocalMap inheritableThreadLocals = null;

    private final long stackSize;

    private long nativeParkEventPointer;

    private final long tid;

    private static long threadSeqNumber;

    private static synchronized long nextThreadID() {
        return ++threadSeqNumber;
    }

    private volatile int threadStatus;

    volatile Object parkBlocker;

    private volatile Interruptible blocker;
    private final Object blockerLock = new Object();

    static void blockedOn(Interruptible b) {
        Thread me = Thread.currentThread();
        synchronized (me.blockerLock) {
            me.blocker = b;
        }
    }

    public static final int MIN_PRIORITY = 1;

    public static final int NORM_PRIORITY = 5;

    public static final int MAX_PRIORITY = 10;

    /**
     * Thread类的静态方法，返回实际执行该代码的Thread对象。
     * @author liuzhen
     * @date 2022/4/15 16:46
     * @param
     * @return java.lang.Thread
     */
    @HotSpotIntrinsicCandidate
    public static native Thread currentThread();

    public static native void yield();

    /**
     * 将线程的执行暂停ms时间。
     * @author liuzhen
     * @date 2022/4/15 16:44
     * @param millis
     * @return void
     */
    public static native void sleep(long millis) throws InterruptedException;

    public static void sleep(long millis, int nanos) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        if (nanos < 0 || nanos > 999999) {
            throw new IllegalArgumentException("nanosecond timeout value out of range");
        }

        if (nanos >= 500000 || (nanos != 0 && millis == 0)) {
            millis++;
        }

        sleep(millis);
    }

    @HotSpotIntrinsicCandidate
    public static void onSpinWait() {
    }

    private Thread(ThreadGroup g, Runnable target, String name, long stackSize, AccessControlContext acc, boolean inheritThreadLocals) {
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }

        this.name = name;

        Thread parent = currentThread();
        SecurityManager security = System.getSecurityManager();
        if (g == null) {
            /* Determine if it's an applet or not */

            /* If there is a security manager, ask the security manager
               what to do. */
            if (security != null) {
                g = security.getThreadGroup();
            }

            /* If the security manager doesn't have a strong opinion
               on the matter, use the parent thread group. */
            if (g == null) {
                g = parent.getThreadGroup();
            }
        }

        /* checkAccess regardless of whether or not threadgroup is
           explicitly passed in. */
        g.checkAccess();

        /*
         * Do we have the required permissions?
         */
        if (security != null) {
            if (isCCLOverridden(getClass())) {
                security.checkPermission(SecurityConstants.SUBCLASS_IMPLEMENTATION_PERMISSION);
            }
        }

        g.addUnstarted();

        this.group = g;
        this.daemon = parent.isDaemon();
        this.priority = parent.getPriority();
        if (security == null || isCCLOverridden(parent.getClass()))
            this.contextClassLoader = parent.getContextClassLoader();
        else
            this.contextClassLoader = parent.contextClassLoader;
        this.inheritedAccessControlContext = acc != null ? acc : AccessController.getContext();
        this.target = target;
        setPriority(priority);
        if (inheritThreadLocals && parent.inheritableThreadLocals != null)
            this.inheritableThreadLocals = ThreadLocal.createInheritedMap(parent.inheritableThreadLocals);
        /* Stash the specified stack size in case the VM cares */
        this.stackSize = stackSize;

        /* Set thread ID */
        this.tid = nextThreadID();
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    public Thread() {
        this(null, null, "Thread-" + nextThreadNum(), 0);
    }

    public Thread(Runnable target) {
        this(null, target, "Thread-" + nextThreadNum(), 0);
    }

    Thread(Runnable target, AccessControlContext acc) {
        this(null, target, "Thread-" + nextThreadNum(), 0, acc, false);
    }

    public Thread(ThreadGroup group, Runnable target) {
        this(group, target, "Thread-" + nextThreadNum(), 0);
    }

    public Thread(String name) {
        this(null, null, name, 0);
    }

    public Thread(ThreadGroup group, String name) {
        this(group, null, name, 0);
    }

    public Thread(Runnable target, String name) {
        this(null, target, name, 0);
    }

    public Thread(ThreadGroup group, Runnable target, String name) {
        this(group, target, name, 0);
    }

    public Thread(ThreadGroup group, Runnable target, String name, long stackSize) {
        this(group, target, name, stackSize, null, true);
    }

    public Thread(ThreadGroup group, Runnable target, String name, long stackSize, boolean inheritThreadLocals) {
        this(group, target, name, stackSize, null, inheritThreadLocals);
    }

    public synchronized void start() {
        if (threadStatus != 0)
            throw new IllegalThreadStateException();

        /* Notify the group that this thread is about to be started
         * so that it can be added to the group's list of threads
         * and the group's unstarted count can be decremented. */
        group.add(this);

        boolean started = false;
        try {
            start0();
            started = true;
        } finally {
            try {
                if (!started) {
                    group.threadStartFailed(this);
                }
            } catch (Throwable ignore) {
                /* do nothing. If start0 threw a Throwable then
                  it will be passed up the call stack */
            }
        }
    }

    private native void start0();

    @Override
    public void run() {
        if (target != null) {
            target.run();
        }
    }

    private void exit() {
        if (threadLocals != null && TerminatingThreadLocal.REGISTRY.isPresent()) {
            TerminatingThreadLocal.threadTerminated();
        }
        if (group != null) {
            group.threadTerminated(this);
            group = null;
        }
        /* Aggressively null out all reference fields: see bug 4006245 */
        target = null;
        /* Speed the release of some of these resources */
        threadLocals = null;
        inheritableThreadLocals = null;
        inheritedAccessControlContext = null;
        blocker = null;
        uncaughtExceptionHandler = null;
    }

    @Deprecated(since = "1.2")
    public final void stop() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            checkAccess();
            if (this != Thread.currentThread()) {
                security.checkPermission(SecurityConstants.STOP_THREAD_PERMISSION);
            }
        }
        // A zero status value corresponds to "NEW", it can't change to
        // not-NEW because we hold the lock.
        if (threadStatus != 0) {
            resume(); // Wake up thread if it was suspended; no-op otherwise
        }

        // The VM can handle all thread states
        stop0(new ThreadDeath());
    }

    /**
     * 中断目标线程，给目标线程发送一个中断信号，线程被打上中断标记。
     * @author liuzhen
     * @date 2022/4/15 16:43
     * @param
     * @return void
     */
    public void interrupt() {
        if (this != Thread.currentThread()) {
            checkAccess();

            // thread may be blocked in an I/O operation
            synchronized (blockerLock) {
                Interruptible b = blocker;
                if (b != null) {
                    interrupt0();  // set interrupt status
                    b.interrupt(this);
                    return;
                }
            }
        }

        // set interrupt status
        interrupt0();
    }

    /**
     * 判断目标线程是否被中断，但是将清除线程的中断标记。
     * @author liuzhen
     * @date 2022/4/15 16:43
     * @param
     * @return boolean
     */
    public static boolean interrupted() {
        return currentThread().isInterrupted(true);
    }

    /**
     * 判断目标线程是否被中断，不会清除中断标记。
     * @author liuzhen
     * @date 2022/4/15 16:43
     * @param
     * @return boolean
     */
    public boolean isInterrupted() {
        return isInterrupted(false);
    }

    @HotSpotIntrinsicCandidate
    private native boolean isInterrupted(boolean ClearInterrupted);

    public final native boolean isAlive();

    @Deprecated(since = "1.2")
    public final void suspend() {
        checkAccess();
        suspend0();
    }

    @Deprecated(since = "1.2")
    public final void resume() {
        checkAccess();
        resume0();
    }

    /**
     * getState()方法获取Thread对象的状态，可以直接更改线程的状态。
     * 在给定时间内， 线程只能处于一个状态。这些状态是JVM使用的状态，不能映射到操作系统的线程状态。
     * @author liuzhen
     * @date 2022/4/15 16:38
     * @param
     * @return long
     */
    public long getId() {
        return tid;
    }

    /**
     * 设置Thread对象的优先级。
     * @author liuzhen
     * @date 2022/4/15 16:40
     * @param newPriority
     * @return void
     */
    public final void setPriority(int newPriority) {
        ThreadGroup g;
        checkAccess();
        if (newPriority > MAX_PRIORITY || newPriority < MIN_PRIORITY) {
            throw new IllegalArgumentException();
        }
        if ((g = getThreadGroup()) != null) {
            if (newPriority > g.getMaxPriority()) {
                newPriority = g.getMaxPriority();
            }
            setPriority0(priority = newPriority);
        }
    }

    /**
     * 获取Thread对象的优先级。
     * @author liuzhen
     * @date 2022/4/15 16:40
     * @param
     * @return int
     */
    public final int getPriority() {
        return priority;
    }

    /**
     * 这两种方法允许你获取或设置Thread对象的名称。这个名
     * 称是一个String对象，也可以在Thread类的构造函数中建立。
     * @author liuzhen
     * @date 2022/4/15 16:39
     * @param name
     * @return void
     */
    public final synchronized void setName(String name) {
        checkAccess();
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }

        this.name = name;
        if (threadStatus != 0) {
            setNativeName(name);
        }
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/15 16:39
     * @param
     * @return java.lang.String
     */
    public final String getName() {
        return name;
    }

    /**
     * 获取Thread对象的守护条件。
     * @author liuzhen
     * @date 2022/4/15 16:42
     * @param
     * @return boolean
     */
    public final boolean isDaemon() {
        return daemon;
    }

    /**
     * 建立Thread对象的守护条件。
     * @author liuzhen
     * @date 2022/4/15 16:42
     * @param on
     * @return void
     */
    public final void setDaemon(boolean on) {
        checkAccess();
        if (isAlive()) {
            throw new IllegalThreadStateException();
        }
        daemon = on;
    }

    public final ThreadGroup getThreadGroup() {
        return group;
    }

    public static int activeCount() {
        return currentThread().getThreadGroup().activeCount();
    }

    public static int enumerate(Thread tarray[]) {
        return currentThread().getThreadGroup().enumerate(tarray);
    }

    @Deprecated(since = "1.2", forRemoval = true)
    public native int countStackFrames();

    public final synchronized void join(long millis) throws InterruptedException {
        long base = System.currentTimeMillis();
        long now = 0;

        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        if (millis == 0) {
            while (isAlive()) {
                wait(0);
            }
        } else {
            while (isAlive()) {
                long delay = millis - now;
                if (delay <= 0) {
                    break;
                }
                wait(delay);
                now = System.currentTimeMillis() - base;
            }
        }
    }

    public final synchronized void join(long millis, int nanos) throws InterruptedException {

        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        if (nanos < 0 || nanos > 999999) {
            throw new IllegalArgumentException("nanosecond timeout value out of range");
        }

        if (nanos >= 500000 || (nanos != 0 && millis == 0)) {
            millis++;
        }

        join(millis);
    }

    /**
     * 暂停线程的执行，直到调用该方法的线程执行结束为止。可以使用该方法等待另一个Thread对象结束。
     * @author liuzhen
     * @date 2022/4/15 16:45
     * @param
     * @return void
     */
    public final void join() throws InterruptedException {
        join(0);
    }

    public static void dumpStack() {
        new Exception("Stack trace").printStackTrace();
    }

    public final void checkAccess() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkAccess(this);
        }
    }

    public String toString() {
        ThreadGroup group = getThreadGroup();
        if (group != null) {
            return "Thread[" + getName() + "," + getPriority() + "," + group.getName() + "]";
        } else {
            return "Thread[" + getName() + "," + getPriority() + "," + "" + "]";
        }
    }

    @CallerSensitive
    public ClassLoader getContextClassLoader() {
        if (contextClassLoader == null)
            return null;
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            ClassLoader.checkClassLoaderPermission(contextClassLoader, Reflection.getCallerClass());
        }
        return contextClassLoader;
    }

    public void setContextClassLoader(ClassLoader cl) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("setContextClassLoader"));
        }
        contextClassLoader = cl;
    }

    public static native boolean holdsLock(Object obj);

    private static final StackTraceElement[] EMPTY_STACK_TRACE = new StackTraceElement[0];

    public StackTraceElement[] getStackTrace() {
        if (this != Thread.currentThread()) {
            // check for getStackTrace permission
            SecurityManager security = System.getSecurityManager();
            if (security != null) {
                security.checkPermission(SecurityConstants.GET_STACK_TRACE_PERMISSION);
            }
            // optimization so we do not call into the vm for threads that
            // have not yet started or have terminated
            if (!isAlive()) {
                return EMPTY_STACK_TRACE;
            }
            StackTraceElement[][] stackTraceArray = dumpThreads(new Thread[] {this});
            StackTraceElement[] stackTrace = stackTraceArray[0];
            // a thread that was alive during the previous isAlive call may have
            // since terminated, therefore not having a stacktrace.
            if (stackTrace == null) {
                stackTrace = EMPTY_STACK_TRACE;
            }
            return stackTrace;
        } else {
            return (new Exception()).getStackTrace();
        }
    }

    public static Map<Thread, StackTraceElement[]> getAllStackTraces() {
        // check for getStackTrace permission
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(SecurityConstants.GET_STACK_TRACE_PERMISSION);
            security.checkPermission(SecurityConstants.MODIFY_THREADGROUP_PERMISSION);
        }

        // Get a snapshot of the list of all threads
        Thread[] threads = getThreads();
        StackTraceElement[][] traces = dumpThreads(threads);
        Map<Thread, StackTraceElement[]> m = new HashMap<>(threads.length);
        for (int i = 0; i < threads.length; i++) {
            StackTraceElement[] stackTrace = traces[i];
            if (stackTrace != null) {
                m.put(threads[i], stackTrace);
            }
            // else terminated so we don't put it in the map
        }
        return m;
    }

    private static class Caches {
        /**
         * cache of subclass security audit results
         */
        static final ConcurrentMap<WeakClassKey, Boolean> subclassAudits = new ConcurrentHashMap<>();

        /**
         * queue for WeakReferences to audited subclasses
         */
        static final ReferenceQueue<Class<?>> subclassAuditsQueue = new ReferenceQueue<>();
    }

    private static boolean isCCLOverridden(Class<?> cl) {
        if (cl == Thread.class)
            return false;

        processQueue(Caches.subclassAuditsQueue, Caches.subclassAudits);
        WeakClassKey key = new WeakClassKey(cl, Caches.subclassAuditsQueue);
        Boolean result = Caches.subclassAudits.get(key);
        if (result == null) {
            result = Boolean.valueOf(auditSubclass(cl));
            Caches.subclassAudits.putIfAbsent(key, result);
        }

        return result.booleanValue();
    }

    private static boolean auditSubclass(final Class<?> subcl) {
        Boolean result = AccessController.doPrivileged(new PrivilegedAction<>() {
            public Boolean run() {
                for (Class<?> cl = subcl; cl != Thread.class; cl = cl.getSuperclass()) {
                    try {
                        cl.getDeclaredMethod("getContextClassLoader", new Class<?>[0]);
                        return Boolean.TRUE;
                    } catch (NoSuchMethodException ex) {
                    }
                    try {
                        Class<?>[] params = {ClassLoader.class};
                        cl.getDeclaredMethod("setContextClassLoader", params);
                        return Boolean.TRUE;
                    } catch (NoSuchMethodException ex) {
                    }
                }
                return Boolean.FALSE;
            }
        });
        return result.booleanValue();
    }

    private static native StackTraceElement[][] dumpThreads(Thread[] threads);

    private static native Thread[] getThreads();

    public enum State {
        /** Thread对象已经创建，但是还没有开始执行。 */
        NEW,
        /** Thread对象已经创建，但是还没有开始执行。 */
        RUNNABLE,
        /** Thread对象正在等待锁定。 */
        BLOCKED,
        /** Thread 对象正在等待另一个线程的动作。 */
        WAITING,
        /** Thread对象正在等待另一个线程的操作，但是有时间限制。 */
        TIMED_WAITING,
        /** Thread对象已经完成了执行。 */
        TERMINATED;
    }

    /**
     * getState()方法获取Thread对象的状态，可以直接更改线程的状态。
     * 在给定时间内，线程只能处于一个状态。这些状态是JVM使用的状态，不能映射到操作系统的线程状态。
     * @author liuzhen
     * @date 2022/4/15 16:36
     * @param
     * @return java.lang.Thread.State
     */
    public State getState() {
        // get current thread state
        return jdk.internal.misc.VM.toThreadState(threadStatus);
    }

    // Added in JSR-166

    @FunctionalInterface
    public interface UncaughtExceptionHandler {
        void uncaughtException(Thread t, Throwable e);
    }

    // null unless explicitly set
    private volatile UncaughtExceptionHandler uncaughtExceptionHandler;

    // null unless explicitly set
    private static volatile UncaughtExceptionHandler defaultUncaughtExceptionHandler;

    public static void setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("setDefaultUncaughtExceptionHandler"));
        }

        defaultUncaughtExceptionHandler = eh;
    }

    public static UncaughtExceptionHandler getDefaultUncaughtExceptionHandler() {
        return defaultUncaughtExceptionHandler;
    }

    public UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return uncaughtExceptionHandler != null ? uncaughtExceptionHandler : group;
    }

    /**
     * 当线程执行出现未校验异常时，该方法用于建立未校验异常的控制器。
     * @author liuzhen
     * @date 2022/4/15 16:45
     * @param eh
     * @return void
     */
    public void setUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
        checkAccess();
        uncaughtExceptionHandler = eh;
    }

    private void dispatchUncaughtException(Throwable e) {
        getUncaughtExceptionHandler().uncaughtException(this, e);
    }

    static void processQueue(ReferenceQueue<Class<?>> queue, ConcurrentMap<? extends WeakReference<Class<?>>, ?> map) {
        Reference<? extends Class<?>> ref;
        while ((ref = queue.poll()) != null) {
            map.remove(ref);
        }
    }

    static class WeakClassKey extends WeakReference<Class<?>> {
        private final int hash;

        WeakClassKey(Class<?> cl, ReferenceQueue<Class<?>> refQueue) {
            super(cl, refQueue);
            hash = System.identityHashCode(cl);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;

            if (obj instanceof WeakClassKey) {
                Object referent = get();
                return (referent != null) && (referent == ((WeakClassKey)obj).get());
            } else {
                return false;
            }
        }
    }

    @jdk.internal.vm.annotation.Contended("tlr")
    long threadLocalRandomSeed;

    @jdk.internal.vm.annotation.Contended("tlr")
    int threadLocalRandomProbe;

    @jdk.internal.vm.annotation.Contended("tlr")
    int threadLocalRandomSecondarySeed;

    private native void setPriority0(int newPriority);

    private native void stop0(Object o);

    private native void suspend0();

    private native void resume0();

    private native void interrupt0();

    private native void setNativeName(String name);
}
