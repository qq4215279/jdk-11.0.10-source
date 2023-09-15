/*
 * Copyright (c) 1994, 2019, Oracle and/or its affiliates. All rights reserved. ORACLE PROPRIETARY/CONFIDENTIAL. Use is
 * subject to license terms.
 */

package java.lang;

import java.io.*;
import java.util.*;

/**
 * 异常类
 * @date 2022/9/10 20:08
 */
public class Throwable implements Serializable {
    private static final long serialVersionUID = -3042686055658047285L;

    private transient Object backtrace;

    private String detailMessage;

    private static final StackTraceElement[] UNASSIGNED_STACK = new StackTraceElement[0];

    private Throwable cause = this;

    private StackTraceElement[] stackTrace = UNASSIGNED_STACK;

    private transient int depth;

    private List<Throwable> suppressedExceptions = SUPPRESSED_SENTINEL;

    private static final List<Throwable> SUPPRESSED_SENTINEL = Collections.emptyList();

    private static final String NULL_CAUSE_MESSAGE = "Cannot suppress a null exception.";
    private static final String SELF_SUPPRESSION_MESSAGE = "Self-suppression not permitted";
    private static final String CAUSE_CAPTION = "Caused by: ";
    private static final String SUPPRESSED_CAPTION = "Suppressed: ";

    public Throwable() {
        fillInStackTrace();
    }

    public Throwable(String message) {
        fillInStackTrace();
        detailMessage = message;
    }

    public Throwable(String message, Throwable cause) {
        fillInStackTrace();
        detailMessage = message;
        this.cause = cause;
    }

    public Throwable(Throwable cause) {
        fillInStackTrace();
        detailMessage = (cause == null ? null : cause.toString());
        this.cause = cause;
    }

    protected Throwable(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        if (writableStackTrace) {
            fillInStackTrace();
        } else {
            stackTrace = null;
        }
        detailMessage = message;
        this.cause = cause;
        if (!enableSuppression)
            suppressedExceptions = null;
    }

    /**
     * 用于设置当前异常的根本原因（引起当前异常的原因）。该方法返回当前异常对象，并且只能在异常对象被创建后调用一次，用于将根本原因与当前异常关联起来。
     * 1. 设置根本原因：
     *  initCause(Throwable cause) 方法用于将指定的异常对象作为当前异常的根本原因。
     *  通常在创建一个新的异常对象后，可以通过调用 initCause() 方法来设置其根本原因。
     * 2. 异常链：
     *  使用 initCause(Throwable cause) 方法可以创建一个异常链，其中当前异常被指定异常作为根本原因。
     *  通过异常链，可以追踪异常的起因和路径，以便更好地理解异常发生的上下文和原因。
     * 3. 循环引用：
     *  initCause(Throwable cause) 方法不允许创建循环引用，即异常不能引用自身或引用导致自身的异常。
     *  如果已经设置了异常的根本原因，再次调用 initCause() 方法将抛出 IllegalStateException 异常。
     * @date 2023/7/13 16:42
     * @param cause
     * @return java.lang.Throwable
     */
    public synchronized Throwable initCause(Throwable cause) {
        if (this.cause != this)
            throw new IllegalStateException("Can't overwrite cause with " + Objects.toString(cause, "a null"), this);
        if (cause == this)
            throw new IllegalArgumentException("Self-causation not permitted", this);
        this.cause = cause;
        return this;
    }

    /**
     * 获取引发当前异常的原因（即根本原因）。该方法返回一个 Throwable 对象，表示引发当前异常的原因，如果没有指定原因，则返回 null。
     * @date 2023/7/13 15:40
     * @param
     * @return java.lang.Throwable
     */
    public synchronized Throwable getCause() {
        return (cause == this ? null : cause);
    }

    public String getLocalizedMessage() {
        return getMessage();
    }

    /**
     * 获取发生异常的原因。 提示给用户的时候,就提示错误原因。
     * @date 2022/9/10 20:23
     * @param
     * @return java.lang.String
     */
    public String getMessage() {
        return detailMessage;
    }

    /**
     * 获取异常的类型和异常描述信息(不用)。
     * @date 2022/9/10 20:24
     * @param
     * @return java.lang.String
     */
    public String toString() {
        String s = getClass().getName();
        String message = getLocalizedMessage();
        return (message != null) ? (s + ": " + message) : s;
    }

    /**
     * 打印异常的详细信息。 包含了异常的类型,异常的原因,还包括异常出现的位置,在开发和调试阶段,都得使用printStackTrace。
     * @date 2022/9/10 20:23
     * @param
     * @return void
     */
    public void printStackTrace() {
        printStackTrace(System.err);
    }

    public void printStackTrace(PrintStream s) {
        printStackTrace(new WrappedPrintStream(s));
    }

    public void printStackTrace(PrintWriter s) {
        printStackTrace(new WrappedPrintWriter(s));
    }

    private void printStackTrace(PrintStreamOrWriter s) {
        // Guard against malicious overrides of Throwable.equals by
        // using a Set with identity equality semantics.
        Set<Throwable> dejaVu = Collections.newSetFromMap(new IdentityHashMap<>());
        dejaVu.add(this);

        synchronized (s.lock()) {
            // Print our stack trace
            s.println(this);
            StackTraceElement[] trace = getOurStackTrace();
            for (StackTraceElement traceElement : trace)
                s.println("\tat " + traceElement);

            // Print suppressed exceptions, if any
            for (Throwable se : getSuppressed())
                se.printEnclosedStackTrace(s, trace, SUPPRESSED_CAPTION, "\t", dejaVu);

            // Print cause, if any
            Throwable ourCause = getCause();
            if (ourCause != null)
                ourCause.printEnclosedStackTrace(s, trace, CAUSE_CAPTION, "", dejaVu);
        }
    }

    private void printEnclosedStackTrace(PrintStreamOrWriter s, StackTraceElement[] enclosingTrace, String caption,
        String prefix, Set<Throwable> dejaVu) {
        assert Thread.holdsLock(s.lock());
        if (dejaVu.contains(this)) {
            s.println("\t[CIRCULAR REFERENCE:" + this + "]");
        } else {
            dejaVu.add(this);
            // Compute number of frames in common between this and enclosing trace
            StackTraceElement[] trace = getOurStackTrace();
            int m = trace.length - 1;
            int n = enclosingTrace.length - 1;
            while (m >= 0 && n >= 0 && trace[m].equals(enclosingTrace[n])) {
                m--;
                n--;
            }
            int framesInCommon = trace.length - 1 - m;

            // Print our stack trace
            s.println(prefix + caption + this);
            for (int i = 0; i <= m; i++)
                s.println(prefix + "\tat " + trace[i]);
            if (framesInCommon != 0)
                s.println(prefix + "\t... " + framesInCommon + " more");

            // Print suppressed exceptions, if any
            for (Throwable se : getSuppressed())
                se.printEnclosedStackTrace(s, trace, SUPPRESSED_CAPTION, prefix + "\t", dejaVu);

            // Print cause, if any
            Throwable ourCause = getCause();
            if (ourCause != null)
                ourCause.printEnclosedStackTrace(s, trace, CAUSE_CAPTION, prefix, dejaVu);
        }
    }

    private abstract static class PrintStreamOrWriter {
        /** Returns the object to be locked when using this StreamOrWriter */
        abstract Object lock();

        /** Prints the specified string as a line on this StreamOrWriter */
        abstract void println(Object o);
    }

    private static class WrappedPrintStream extends PrintStreamOrWriter {
        private final PrintStream printStream;

        WrappedPrintStream(PrintStream printStream) {
            this.printStream = printStream;
        }

        Object lock() {
            return printStream;
        }

        void println(Object o) {
            printStream.println(o);
        }
    }

    private static class WrappedPrintWriter extends PrintStreamOrWriter {
        private final PrintWriter printWriter;

        WrappedPrintWriter(PrintWriter printWriter) {
            this.printWriter = printWriter;
        }

        Object lock() {
            return printWriter;
        }

        void println(Object o) {
            printWriter.println(o);
        }
    }

    /**
     * 更新异常的堆栈跟踪信息，通常用于异常重新抛出时更新异常的堆栈信息(即新异常的堆栈跟踪信息指向 fillInStackTrace() 方法被调用的地方)。
     * 该方法返回更新后的异常对象。产生一定的性能开销。
     *
     * 特点和使用场景：
     *  更新堆栈跟踪信息：
     *      fillInStackTrace() 方法会重新计算和填充异常的堆栈跟踪信息，以反映当前的调用状态。
     *      默认情况下，异常的堆栈跟踪信息是在抛出异常的地方生成的，但有时可能需要在其他地方重新生成堆栈跟踪信息。
     *  异常重新抛出：
     *      在异常被捕获并重新抛出时，通常可以调用 fillInStackTrace() 方法来更新异常的堆栈跟踪信息。
     *      通过重新填充堆栈跟踪信息，可以获得更准确的异常调用链，便于定位异常的起因和路径。
     *  性能开销：
     *      由于 fillInStackTrace() 方法会重新计算堆栈跟踪信息，因此可能会产生一定的性能开销。
     *      在性能敏感的场景中，需要谨慎使用该方法，以避免不必要的性能损耗。
     * @date 2023/7/13 16:38
     * @param
     * @return java.lang.Throwable
     */
    public synchronized Throwable fillInStackTrace() {
        if (stackTrace != null || backtrace != null /* Out of protocol state */ ) {
            fillInStackTrace(0);
            stackTrace = UNASSIGNED_STACK;
        }
        return this;
    }

    private native Throwable fillInStackTrace(int dummy);

    public StackTraceElement[] getStackTrace() {
        return getOurStackTrace().clone();
    }

    /**
     * 获取堆栈信息（底层从native api 获得）
     * @date 2023/8/4 16:18
     * @param
     * @return java.lang.StackTraceElement[]
     */
    private synchronized StackTraceElement[] getOurStackTrace() {
        // Initialize stack trace field with information from
        // backtrace if this is the first call to this method
        if (stackTrace == UNASSIGNED_STACK || (stackTrace == null && backtrace != null) /* Out of protocol state */) {
            stackTrace = StackTraceElement.of(this, depth);
        } else if (stackTrace == null) {
            return UNASSIGNED_STACK;
        }
        return stackTrace;
    }

    public void setStackTrace(StackTraceElement[] stackTrace) {
        // Validate argument
        StackTraceElement[] defensiveCopy = stackTrace.clone();
        for (int i = 0; i < defensiveCopy.length; i++) {
            if (defensiveCopy[i] == null)
                throw new NullPointerException("stackTrace[" + i + "]");
        }

        synchronized (this) {
            if (this.stackTrace == null && // Immutable stack
                backtrace == null) // Test for out of protocol state
                return;
            this.stackTrace = defensiveCopy;
        }
    }

    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject(); // read in all fields

        // Set suppressed exceptions and stack trace elements fields
        // to marker values until the contents from the serial stream
        // are validated.
        List<Throwable> candidateSuppressedExceptions = suppressedExceptions;
        suppressedExceptions = SUPPRESSED_SENTINEL;

        StackTraceElement[] candidateStackTrace = stackTrace;
        stackTrace = UNASSIGNED_STACK.clone();

        if (candidateSuppressedExceptions != null) {
            int suppressedSize = validateSuppressedExceptionsList(candidateSuppressedExceptions);
            if (suppressedSize > 0) { // Copy valid Throwables to new list
                var suppList = new ArrayList<Throwable>(Math.min(100, suppressedSize));

                for (Throwable t : candidateSuppressedExceptions) {
                    // Enforce constraints on suppressed exceptions in
                    // case of corrupt or malicious stream.
                    if (t == null)
                        throw new NullPointerException(NULL_CAUSE_MESSAGE);
                    if (t == this)
                        throw new IllegalArgumentException(SELF_SUPPRESSION_MESSAGE);
                    suppList.add(t);
                }
                // If there are any invalid suppressed exceptions,
                // implicitly use the sentinel value assigned earlier.
                suppressedExceptions = suppList;
            }
        } else {
            suppressedExceptions = null;
        }

        /*
         * For zero-length stack traces, use a clone of
         * UNASSIGNED_STACK rather than UNASSIGNED_STACK itself to
         * allow identity comparison against UNASSIGNED_STACK in
         * getOurStackTrace.  The identity of UNASSIGNED_STACK in
         * stackTrace indicates to the getOurStackTrace method that
         * the stackTrace needs to be constructed from the information
         * in backtrace.
         */
        if (candidateStackTrace != null) {
            // Work from a clone of the candidateStackTrace to ensure
            // consistency of checks.
            candidateStackTrace = candidateStackTrace.clone();
            if (candidateStackTrace.length >= 1) {
                if (candidateStackTrace.length == 1 &&
                // Check for the marker of an immutable stack trace
                    SentinelHolder.STACK_TRACE_ELEMENT_SENTINEL.equals(candidateStackTrace[0])) {
                    stackTrace = null;
                } else { // Verify stack trace elements are non-null.
                    for (StackTraceElement ste : candidateStackTrace) {
                        if (ste == null)
                            throw new NullPointerException("null StackTraceElement in serial stream.");
                    }
                    stackTrace = candidateStackTrace;
                }
            }
        }
        // A null stackTrace field in the serial form can result from
        // an exception serialized without that field in older JDK
        // releases; treat such exceptions as having empty stack
        // traces by leaving stackTrace assigned to a clone of
        // UNASSIGNED_STACK.
    }

    private int validateSuppressedExceptionsList(List<Throwable> deserSuppressedExceptions) throws IOException {
        if (!Object.class.getModule().equals(deserSuppressedExceptions.getClass().getModule())) {
            throw new StreamCorruptedException("List implementation not in base module.");
        } else {
            int size = deserSuppressedExceptions.size();
            if (size < 0) {
                throw new StreamCorruptedException("Negative list size reported.");
            }
            return size;
        }
    }

    private synchronized void writeObject(ObjectOutputStream s) throws IOException {
        // Ensure that the stackTrace field is initialized to a
        // non-null value, if appropriate. As of JDK 7, a null stack
        // trace field is a valid value indicating the stack trace
        // should not be set.
        getOurStackTrace();

        StackTraceElement[] oldStackTrace = stackTrace;
        try {
            if (stackTrace == null)
                stackTrace = SentinelHolder.STACK_TRACE_SENTINEL;
            s.defaultWriteObject();
        } finally {
            stackTrace = oldStackTrace;
        }
    }

    public final synchronized void addSuppressed(Throwable exception) {
        if (exception == this)
            throw new IllegalArgumentException(SELF_SUPPRESSION_MESSAGE, exception);

        if (exception == null)
            throw new NullPointerException(NULL_CAUSE_MESSAGE);

        if (suppressedExceptions == null) // Suppressed exceptions not recorded
            return;

        if (suppressedExceptions == SUPPRESSED_SENTINEL)
            suppressedExceptions = new ArrayList<>(1);

        suppressedExceptions.add(exception);
    }

    private static final Throwable[] EMPTY_THROWABLE_ARRAY = new Throwable[0];

    public final synchronized Throwable[] getSuppressed() {
        if (suppressedExceptions == SUPPRESSED_SENTINEL || suppressedExceptions == null)
            return EMPTY_THROWABLE_ARRAY;
        else
            return suppressedExceptions.toArray(EMPTY_THROWABLE_ARRAY);
    }

    private static class SentinelHolder {
        public static final StackTraceElement STACK_TRACE_ELEMENT_SENTINEL =
            new StackTraceElement("", "", null, Integer.MIN_VALUE);

        public static final StackTraceElement[] STACK_TRACE_SENTINEL =
            new StackTraceElement[] {STACK_TRACE_ELEMENT_SENTINEL};
    }
}
