/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.framemap;

import static org.graalvm.compiler.lir.LIRValueUtil.isVirtualStackSlot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.InstructionValueConsumer;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * A FrameMapBuilder that records allocation.
 */
public class FrameMapBuilderImpl extends FrameMapBuilderTool {

    private final RegisterConfig registerConfig;
    private final CodeCacheProvider codeCache;
    private final FrameMap frameMap;
    private final List<VirtualStackSlot> stackSlots;
    private final List<CallingConvention> calls;
    private int numStackSlots;

    public FrameMapBuilderImpl(FrameMap frameMap, CodeCacheProvider codeCache, RegisterConfig registerConfig) {
        assert registerConfig != null : "No register config!";
        this.registerConfig = registerConfig == null ? codeCache.getRegisterConfig() : registerConfig;
        this.codeCache = codeCache;
        this.frameMap = frameMap;
        this.stackSlots = new ArrayList<>();
        this.calls = new ArrayList<>();
        this.numStackSlots = 0;
    }

    @Override
    public VirtualStackSlot allocateSpillSlot(ValueKind<?> kind) {
        SimpleVirtualStackSlot slot = new SimpleVirtualStackSlot(numStackSlots++, kind);
        stackSlots.add(slot);
        return slot;
    }

    @Override
    public VirtualStackSlot allocateStackSlots(int slots) {
        if (slots == 0) {
            return null;
        }
        VirtualStackSlotRange slot = new VirtualStackSlotRange(numStackSlots++, slots, LIRKind.value(frameMap.getTarget().arch.getWordKind()));
        stackSlots.add(slot);
        return slot;
    }

    @Override
    public RegisterConfig getRegisterConfig() {
        return registerConfig;
    }

    @Override
    public CodeCacheProvider getCodeCache() {
        return codeCache;
    }

    @Override
    public FrameMap getFrameMap() {
        return frameMap;
    }

    @Override
    public int getNumberOfStackSlots() {
        return numStackSlots;
    }

    @Override
    public void callsMethod(CallingConvention cc) {
        calls.add(cc);
    }

    @Override
    @SuppressWarnings("try")
    public FrameMap buildFrameMap(LIRGenerationResult res) {
        DebugContext debug = res.getLIR().getDebug();
        if (debug.areScopesEnabled()) {
            verifyStackSlotAllocation(res);
        }
        for (CallingConvention cc : calls) {
            frameMap.callsMethod(cc);
        }
        frameMap.finish();
        return frameMap;
    }

    private static void verifyStackSlotAllocation(LIRGenerationResult res) {
        LIR lir = res.getLIR();
        InstructionValueConsumer verifySlots = (LIRInstruction op, Value value, OperandMode mode, EnumSet<OperandFlag> flags) -> {
            assert !isVirtualStackSlot(value) : String.format("Instruction %s contains a virtual stack slot %s", op, value);
        };
        for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
            lir.getLIRforBlock(block).forEach(op -> {
                op.visitEachInput(verifySlots);
                op.visitEachAlive(verifySlots);
                op.visitEachState(verifySlots);

                op.visitEachTemp(verifySlots);
                op.visitEachOutput(verifySlots);
            });
        }
    }

    @Override
    public List<VirtualStackSlot> getStackSlots() {
        return stackSlots;
    }

}
