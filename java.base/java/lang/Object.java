/*
 * Copyright (c) 1994, 2017, Oracle and/or its affiliates. All rights reserved. ORACLE PROPRIETARY/CONFIDENTIAL. Use is
 * subject to license terms.
 */

package java.lang;

import jdk.internal.HotSpotIntrinsicCandidate;

/** 
 * Object
 * @author liuzhen
 * @date 2022/4/10 23:38
 */
public class Object {

    private static native void registerNatives();

    static {
        registerNatives();
    }

    @HotSpotIntrinsicCandidate
    public Object() {}

    @HotSpotIntrinsicCandidate
    protected native Object clone() throws CloneNotSupportedException;

    public boolean equals(Object obj) {
        return (this == obj);
    }

    @Deprecated(since = "9")
    protected void finalize() throws Throwable {}

    @HotSpotIntrinsicCandidate
    public final native Class<?> getClass();

    @HotSpotIntrinsicCandidate
    public native int hashCode();

    @HotSpotIntrinsicCandidate
    public final native void notify();

    @HotSpotIntrinsicCandidate
    public final native void notifyAll();

    public String toString() {
        return getClass().getName() + "@" + Integer.toHexString(hashCode());
    }

    public final void wait() throws InterruptedException {
        wait(0L);
    }

    public final void wait(long timeoutMillis, int nanos) throws InterruptedException {
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("timeoutMillis value is negative");
        }

        if (nanos < 0 || nanos > 999999) {
            throw new IllegalArgumentException("nanosecond timeout value out of range");
        }

        if (nanos > 0) {
            timeoutMillis++;
        }

        wait(timeoutMillis);
    }

    public final native void wait(long timeoutMillis) throws InterruptedException;
}
