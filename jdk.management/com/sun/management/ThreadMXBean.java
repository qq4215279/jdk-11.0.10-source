/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package com.sun.management;

import java.util.Map;

/**
 * Platform-specific management interface for the thread system
 * of the Java virtual machine.
 * <p>
 * This platform extension is only available to a thread
 * implementation that supports this extension.
 *
 * @author  Paul Hohensee
 * @since   6u25
 */

public interface ThreadMXBean extends java.lang.management.ThreadMXBean {
    /**
     * Returns the total CPU time for each thread whose ID is
     * in the input array {@code ids} in nanoseconds.
     * The returned values are of nanoseconds precision but
     * not necessarily nanoseconds accuracy.
     * <p>
     * This method is equivalent to calling the
     * {@link ThreadMXBean#getThreadCpuTime(long)}
     * method for each thread ID in the input array {@code ids} and setting the
     * returned value in the corresponding element of the returned array.
     *
     * @param ids an array of thread IDs.
     * @return an array of long values, each of which is the amount of CPU
     * time the thread whose ID is in the corresponding element of the input
     * array of IDs has used,
     * if the thread of a specified ID exists, the thread is alive,
     * and CPU time measurement is enabled;
     * {@code -1} otherwise.
     *
     * @throws NullPointerException if {@code ids} is {@code null}
     * @throws IllegalArgumentException if any element in the input array
     *         {@code ids} is {@code <=} {@code 0}.
     * @throws UnsupportedOperationException if the Java
     *         virtual machine implementation does not support CPU time
     *         measurement.
     *
     * @see ThreadMXBean#getThreadCpuTime(long)
     * @see #getThreadUserTime
     * @see ThreadMXBean#isThreadCpuTimeSupported
     * @see ThreadMXBean#isThreadCpuTimeEnabled
     * @see ThreadMXBean#setThreadCpuTimeEnabled
     */
    public long[] getThreadCpuTime(long[] ids);

    /**
     * Returns the CPU time that each thread whose ID is in the input array
     * {@code ids} has executed in user mode in nanoseconds.
     * The returned values are of nanoseconds precision but
     * not necessarily nanoseconds accuracy.
     * <p>
     * This method is equivalent to calling the
     * {@link ThreadMXBean#getThreadUserTime(long)}
     * method for each thread ID in the input array {@code ids} and setting the
     * returned value in the corresponding element of the returned array.
     *
     * @param ids an array of thread IDs.
     * @return an array of long values, each of which is the amount of user
     * mode CPU time the thread whose ID is in the corresponding element of
     * the input array of IDs has used,
     * if the thread of a specified ID exists, the thread is alive,
     * and CPU time measurement is enabled;
     * {@code -1} otherwise.
     *
     * @throws NullPointerException if {@code ids} is {@code null}
     * @throws IllegalArgumentException if any element in the input array
     *         {@code ids} is {@code <=} {@code 0}.
     * @throws UnsupportedOperationException if the Java
     *         virtual machine implementation does not support CPU time
     *         measurement.
     *
     * @see ThreadMXBean#getThreadUserTime(long)
     * @see #getThreadCpuTime
     * @see ThreadMXBean#isThreadCpuTimeSupported
     * @see ThreadMXBean#isThreadCpuTimeEnabled
     * @see ThreadMXBean#setThreadCpuTimeEnabled
     */
    public long[] getThreadUserTime(long[] ids);

    /**
     * Returns an approximation of the total amount of memory, in bytes,
     * allocated in heap memory for the current thread.
     * The returned value is an approximation because some Java virtual machine
     * implementations may use object allocation mechanisms that result in a
     * delay between the time an object is allocated and the time its size is
     * recorded.
     *
     * <p>
     * This is a convenience method for local management use and is
     * equivalent to calling:
     * <blockquote><pre>
     *   {@link #getThreadAllocatedBytes getThreadAllocatedBytes}(Thread.currentThread().getId());
     * </pre></blockquote>
     *
     * @return an approximation of the total memory allocated, in bytes, in
     * heap memory for the current thread
     * if thread memory allocation measurement is enabled;
     * {@code -1} otherwise.
     *
     * @throws UnsupportedOperationException if the Java virtual
     *         machine implementation does not support thread memory allocation
     *         measurement.
     *
     * @see #isThreadAllocatedMemorySupported
     * @see #isThreadAllocatedMemoryEnabled
     * @see #setThreadAllocatedMemoryEnabled
     */
    public default long getCurrentThreadAllocatedBytes() {
        return getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    /**
     * Returns an approximation of the total amount of memory, in bytes,
     * allocated in heap memory for the thread with the specified ID.
     * The returned value is an approximation because some Java virtual machine
     * implementations may use object allocation mechanisms that result in a
     * delay between the time an object is allocated and the time its size is
     * recorded.
     * <p>
     * If the thread with the specified ID is not alive or does not exist,
     * this method returns {@code -1}. If thread memory allocation measurement
     * is disabled, this method returns {@code -1}.
     * A thread is alive if it has been started and has not yet died.
     * <p>
     * If thread memory allocation measurement is enabled after the thread has
     * started, the Java virtual machine implementation may choose any time up
     * to and including the time that the capability is enabled as the point
     * where thread memory allocation measurement starts.
     *
     * @param id the thread ID of a thread
     * @return an approximation of the total memory allocated, in bytes, in
     * heap memory for the thread with the specified ID
     * if the thread with the specified ID exists, the thread is alive,
     * and thread memory allocation measurement is enabled;
     * {@code -1} otherwise.
     *
     * @throws IllegalArgumentException if {@code id} {@code <=} {@code 0}.
     * @throws UnsupportedOperationException if the Java virtual
     *         machine implementation does not support thread memory allocation
     *         measurement.
     *
     * @see #isThreadAllocatedMemorySupported
     * @see #isThreadAllocatedMemoryEnabled
     * @see #setThreadAllocatedMemoryEnabled
     */
    public long getThreadAllocatedBytes(long id);

    /**
     * Returns an approximation of the total amount of memory, in bytes,
     * allocated in heap memory for each thread whose ID is in the input
     * array {@code ids}.
     * The returned values are approximations because some Java virtual machine
     * implementations may use object allocation mechanisms that result in a
     * delay between the time an object is allocated and the time its size is
     * recorded.
     * <p>
     * This method is equivalent to calling the
     * {@link #getThreadAllocatedBytes(long)}
     * method for each thread ID in the input array {@code ids} and setting the
     * returned value in the corresponding element of the returned array.
     *
     * @param ids an array of thread IDs.
     * @return an array of long values, each of which is an approximation of
     * the total memory allocated, in bytes, in heap memory for the thread
     * whose ID is in the corresponding element of the input array of IDs.
     *
     * @throws NullPointerException if {@code ids} is {@code null}
     * @throws IllegalArgumentException if any element in the input array
     *         {@code ids} is {@code <=} {@code 0}.
     * @throws UnsupportedOperationException if the Java virtual
     *         machine implementation does not support thread memory allocation
     *         measurement.
     *
     * @see #getThreadAllocatedBytes(long)
     * @see #isThreadAllocatedMemorySupported
     * @see #isThreadAllocatedMemoryEnabled
     * @see #setThreadAllocatedMemoryEnabled
     */
    public long[] getThreadAllocatedBytes(long[] ids);

    /**
     * Tests if the Java virtual machine implementation supports thread memory
     * allocation measurement.
     *
     * @return
     *   {@code true}
     *     if the Java virtual machine implementation supports thread memory
     *     allocation measurement;
     *   {@code false} otherwise.
     */
    public boolean isThreadAllocatedMemorySupported();

    /**
     * Tests if thread memory allocation measurement is enabled.
     *
     * @return {@code true} if thread memory allocation measurement is enabled;
     *         {@code false} otherwise.
     *
     * @throws UnsupportedOperationException if the Java virtual
     *         machine does not support thread memory allocation measurement.
     *
     * @see #isThreadAllocatedMemorySupported
     */
    public boolean isThreadAllocatedMemoryEnabled();

    /**
     * Enables or disables thread memory allocation measurement.  The default
     * is platform dependent.
     *
     * @param enable {@code true} to enable;
     *               {@code false} to disable.
     *
     * @throws UnsupportedOperationException if the Java virtual
     *         machine does not support thread memory allocation measurement.
     *
     * @throws SecurityException if a security manager
     *         exists and the caller does not have
     *         ManagementPermission("control").
     *
     * @see #isThreadAllocatedMemorySupported
     */
    public void setThreadAllocatedMemoryEnabled(boolean enable);
}