/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.sparc;

import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.NativeCall;
import static jdk.vm.ci.meta.Value.ILLEGAL;
import static jdk.vm.ci.sparc.SPARC.i0;
import static jdk.vm.ci.sparc.SPARC.i1;
import static jdk.vm.ci.sparc.SPARC.o0;
import static jdk.vm.ci.sparc.SPARC.o1;
import static org.graalvm.compiler.hotspot.HotSpotBackend.EXCEPTION_HANDLER;
import static org.graalvm.compiler.hotspot.HotSpotBackend.EXCEPTION_HANDLER_IN_CALLER;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.JUMP_ADDRESS;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.Reexecutability.NOT_REEXECUTABLE;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.PRESERVES_REGISTERS;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.Transition.LEAF_NO_VZERO;
import static org.graalvm.compiler.hotspot.replacements.CRC32CSubstitutions.UPDATE_BYTES_CRC32C;
import static org.graalvm.compiler.hotspot.replacements.CRC32Substitutions.UPDATE_BYTES_CRC32;
import static jdk.internal.vm.compiler.word.LocationIdentity.any;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkageImpl;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

public class SPARCHotSpotForeignCallsProvider extends HotSpotHostForeignCallsProvider {

    private final Value[] nativeABICallerSaveRegisters;

    public SPARCHotSpotForeignCallsProvider(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, CodeCacheProvider codeCache,
                    WordTypes wordTypes, Value[] nativeABICallerSaveRegisters) {
        super(jvmciRuntime, runtime, metaAccess, codeCache, wordTypes);
        this.nativeABICallerSaveRegisters = nativeABICallerSaveRegisters;
    }

    @Override
    public void initialize(HotSpotProviders providers, OptionValues options) {
        GraalHotSpotVMConfig config = runtime.getVMConfig();
        TargetDescription target = providers.getCodeCache().getTarget();
        PlatformKind word = target.arch.getWordKind();

        // The calling convention for the exception handler stub is (only?) defined in
        // TemplateInterpreterGenerator::generate_throw_exception()
        // in templateInterpreter_sparc.cpp around line 1925
        RegisterValue outgoingException = o0.asValue(LIRKind.fromJavaKind(target.arch, JavaKind.Object));
        RegisterValue outgoingExceptionPc = o1.asValue(LIRKind.value(word));
        RegisterValue incomingException = i0.asValue(LIRKind.fromJavaKind(target.arch, JavaKind.Object));
        RegisterValue incomingExceptionPc = i1.asValue(LIRKind.value(word));
        CallingConvention outgoingExceptionCc = new CallingConvention(0, ILLEGAL, outgoingException, outgoingExceptionPc);
        CallingConvention incomingExceptionCc = new CallingConvention(0, ILLEGAL, incomingException, incomingExceptionPc);
        register(new HotSpotForeignCallLinkageImpl(EXCEPTION_HANDLER, 0L, PRESERVES_REGISTERS, LEAF_NO_VZERO, NOT_REEXECUTABLE, outgoingExceptionCc, incomingExceptionCc, any()));
        register(new HotSpotForeignCallLinkageImpl(EXCEPTION_HANDLER_IN_CALLER, JUMP_ADDRESS, PRESERVES_REGISTERS, LEAF_NO_VZERO, NOT_REEXECUTABLE, outgoingExceptionCc,
                        incomingExceptionCc, any()));

        if (config.useCRC32Intrinsics) {
            // This stub does callee saving
            registerForeignCall(UPDATE_BYTES_CRC32, config.updateBytesCRC32Stub, NativeCall, PRESERVES_REGISTERS, LEAF_NO_VZERO, NOT_REEXECUTABLE, any());
        }
        if (config.useCRC32CIntrinsics) {
            registerForeignCall(UPDATE_BYTES_CRC32C, config.updateBytesCRC32C, NativeCall, PRESERVES_REGISTERS, LEAF_NO_VZERO, NOT_REEXECUTABLE, any());
        }

        super.initialize(providers, options);
    }

    @Override
    public Value[] getNativeABICallerSaveRegisters() {
        return nativeABICallerSaveRegisters;
    }
}
