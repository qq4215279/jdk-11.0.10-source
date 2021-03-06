/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.lir.test;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.jtt.LIRTest;
import org.graalvm.compiler.lir.jtt.LIRTestSpecification;
import org.graalvm.compiler.lir.stackslotalloc.LSStackSlotAllocator;
import org.graalvm.compiler.nodes.SafepointNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests the mitigation against overflowing the max size limit for a HotSpot OopMap. The mitigation
 * works by {@link LSStackSlotAllocator} placing reference typed stack slots at lower offsets.
 */
public class MitigateExceedingMaxOopMapStackOffsetTest extends LIRTest {

    /**
     * Allocate stacks slots and initializes those at an odd index with a reference constant and
     * those at an even index with a primitive constant.
     */
    private static class WriteStackValues extends LIRTestSpecification {
        private final JavaConstant objectConstant;
        private final JavaConstant primitiveConstant;

        WriteStackValues(JavaConstant objectConstant, JavaConstant primitiveConstant) {
            this.objectConstant = objectConstant;
            this.primitiveConstant = primitiveConstant;
        }

        @Override
        public void generate(LIRGeneratorTool gen) {
            FrameMapBuilder frameMapBuilder = gen.getResult().getFrameMapBuilder();
            LIRKind objectLirKind = LIRKind.reference(gen.target().arch.getPlatformKind(objectConstant.getJavaKind()));
            LIRKind primitiveLirKind = LIRKind.value(gen.target().arch.getPlatformKind(primitiveConstant.getJavaKind()));

            int numSlots = numPrimitiveSlots + numReferenceSlots;
            List<AllocatableValue> slotList = new ArrayList<>(numSlots);
            // Place reference slots at top and bottom of virtual frame
            // with primitive slots in the middle. This tests that slot
            // partitioning works.
            for (int i = 0; i < numReferenceSlots / 2; i++) {
                AllocatableValue src = gen.emitLoadConstant(objectLirKind, objectConstant);
                VirtualStackSlot slot = frameMapBuilder.allocateSpillSlot(objectLirKind);
                slotList.add(slot);
                gen.emitMove(slot, src);
            }
            for (int i = 0; i < numPrimitiveSlots; i++) {
                AllocatableValue src = gen.emitLoadConstant(objectLirKind, primitiveConstant);
                VirtualStackSlot slot = frameMapBuilder.allocateSpillSlot(primitiveLirKind);
                slotList.add(slot);
                gen.emitMove(slot, src);
            }
            for (int i = numReferenceSlots / 2; i < numReferenceSlots; i++) {
                AllocatableValue src = gen.emitLoadConstant(objectLirKind, objectConstant);
                VirtualStackSlot slot = frameMapBuilder.allocateSpillSlot(objectLirKind);
                slotList.add(slot);
                gen.emitMove(slot, src);
            }
            slots = slotList.toArray(new AllocatableValue[slotList.size()]);
        }
    }

    /**
     * Read stacks slots and move their content into a blackhole.
     */
    private static class ReadStackValues extends LIRTestSpecification {

        ReadStackValues() {
        }

        @Override
        public void generate(LIRGeneratorTool gen) {
            for (int i = 0; i < slots.length; i++) {
                gen.emitBlackhole(gen.emitMove(slots[i]));
            }
        }
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        InvocationPlugin safepointPlugin = new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new SafepointNode());
                return true;
            }
        };
        conf.getPlugins().getInvocationPlugins().register(safepointPlugin, getClass(), "safepoint");
        return super.editGraphBuilderConfiguration(conf);
    }

    /*
     * Safepoint Snippet
     */
    private static void safepoint() {
    }

    private static int numPrimitiveSlots;
    private static int numReferenceSlots;
    private static AllocatableValue[] slots;

    private static final LIRTestSpecification readStackValues = new ReadStackValues();

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static void instrinsic(LIRTestSpecification spec) {
    }

    private static final LIRTestSpecification writeStackValues = new WriteStackValues(JavaConstant.NULL_POINTER, JavaConstant.LONG_0);

    public void testStackObjects() {
        instrinsic(writeStackValues);
        safepoint();
        instrinsic(readStackValues);
    }

    @Test
    public void runStackObjects() {
        int max = ((HotSpotBackend) getBackend()).getRuntime().getVMConfig().maxOopMapStackOffset;
        Assume.assumeFalse("no limit on oop map size", max == Integer.MAX_VALUE);
        numPrimitiveSlots = (max / 8) * 2;
        numReferenceSlots = (max / 8) - 100; // Should be enough margin for all platforms
        runTest("testStackObjects");
    }
}
