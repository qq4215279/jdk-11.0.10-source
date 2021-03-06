/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
 */


package org.graalvm.compiler.hotspot;

import jdk.vm.ci.meta.InvokeTarget;

import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.hotspot.stubs.Stub;
import jdk.internal.vm.compiler.word.LocationIdentity;

/**
 * The details required to link a HotSpot runtime or stub call.
 */
public interface HotSpotForeignCallLinkage extends ForeignCallLinkage, InvokeTarget {

    /**
     * Constants for specifying whether a foreign call destroys or preserves registers. A foreign
     * call will always destroy {@link HotSpotForeignCallLinkage#getOutgoingCallingConvention() its}
     * {@linkplain ForeignCallLinkage#getTemporaries() temporary} registers.
     */
    enum RegisterEffect {
        DESTROYS_REGISTERS,
        PRESERVES_REGISTERS
    }

    /**
     * Constants for specifying whether a call is a leaf or not and whether a
     * {@code JavaFrameAnchor} prologue and epilogue is required around the call. A leaf function
     * does not lock, GC or throw exceptions.
     */
    enum Transition {
        /**
         * A call to a leaf function that is guaranteed to not use floating point registers.
         * Consequently, floating point registers cleanup will be waived. On AMD64, this means the
         * compiler will no longer emit vzeroupper instruction around the foreign call, which it
         * normally does for unknown foreign calls to avoid potential SSE-AVX transition penalty.
         * Besides, this foreign call will never have its caller stack inspected by the VM. That is,
         * {@code JavaFrameAnchor} management around the call can be omitted.
         */
        LEAF_NO_VZERO,

        /**
         * A call to a leaf function that might use floating point registers but will never have its
         * caller stack inspected. That is, {@code JavaFrameAnchor} management around the call can
         * be omitted.
         */
        LEAF,

        /**
         * A call to a leaf function that might use floating point registers and may have its caller
         * stack inspected. That is, {@code JavaFrameAnchor} management code around the call is
         * required.
         */
        STACK_INSPECTABLE_LEAF,

        /**
         * A function that may lock, GC or raise an exception and thus requires debug info to be
         * associated with a call site to the function. The execution stack may be inspected while
         * in the called function. That is, {@code JavaFrameAnchor} management code around the call
         * is required.
         */
        SAFEPOINT,
    }

    /**
     * Constants specifying when a foreign call or stub call is re-executable.
     */
    enum Reexecutability {
        /**
         * Denotes a call that cannot be re-executed. If an exception is raised, the call is
         * deoptimized and the exception is passed on to be dispatched. If the call can throw an
         * exception it needs to have a precise frame state.
         */
        NOT_REEXECUTABLE,

        /**
         * Denotes a call that can always be re-executed. If an exception is raised by the call it
         * may be cleared, compiled code deoptimized and reexecuted. Since the call has no side
         * effects it is assumed that the same exception will be thrown.
         */
        REEXECUTABLE
    }

    /**
     * Sentinel marker for a computed jump address.
     */
    long JUMP_ADDRESS = 0xDEADDEADBEEFBEEFL;

    /**
     * Determines if the call has side effects.
     */
    boolean isReexecutable();

    LocationIdentity[] getKilledLocations();

    void setCompiledStub(Stub stub);

    /**
     * Determines if this is a call to a compiled {@linkplain Stub stub}.
     */
    boolean isCompiledStub();

    /**
     * Gets the stub, if any, this foreign call links to.
     */
    Stub getStub();

    void finalizeAddress(Backend backend);

    long getAddress();

    /**
     * Determines if the runtime function or stub might use floating point registers. If the answer
     * is no, then no FPU state management prologue or epilogue needs to be emitted around the call.
     */
    boolean mayContainFP();

    /**
     * Determines if a {@code JavaFrameAnchor} needs to be set up and torn down around this call.
     */
    boolean needsJavaFrameAnchor();

    /**
     * Gets the VM symbol associated with the target {@linkplain #getAddress() address} of the call.
     */
    String getSymbol();

    /**
     * Identifies foreign calls which are guaranteed to include a safepoint check.
     */
    boolean isGuaranteedSafepoint();
}
