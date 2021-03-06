/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createStandardInlineInfo;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.replacements.CachingPEGraphDecoder;
import jdk.internal.vm.compiler.word.LocationIdentity;
import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class PEGraphDecoderTest extends GraalCompilerTest {

    /**
     * This method is intrinsified to a node with a guard dependency on the block it is in. The
     * various tests ensure that this guard is correctly updated when blocks are merged during
     * inlining.
     */
    private static native int readInt(Object obj, long offset);

    private static boolean flag;
    private static int value;

    private static void invokeSimple() {
        value = 111;
    }

    private static void invokeComplicated() {
        if (flag) {
            value = 0;
        } else {
            value = 42;
        }
    }

    private static int readInt1(Object obj) {
        return readInt(obj, 16);
    }

    private static int readInt2(Object obj) {
        invokeSimple();
        return readInt(obj, 16);
    }

    private static int readInt3(Object obj) {
        invokeComplicated();
        return readInt(obj, 16);
    }

    private static int readInt4(Object obj, int n) {
        if (n > 0) {
            invokeComplicated();
        }
        return readInt(obj, 16);
    }

    public static int doTest(Object obj) {
        int result = 0;
        result += readInt1(obj);
        result += readInt2(obj);
        result += readInt3(obj);
        result += readInt4(obj, 2);
        return result;
    }

    private static void registerPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, PEGraphDecoderTest.class);
        r.register2("readInt", Object.class, long.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode obj, ValueNode offset) {
                AddressNode address = b.add(new OffsetAddressNode(obj, offset));
                ReadNode read = b.addPush(JavaKind.Int, new ReadNode(address, LocationIdentity.any(), StampFactory.forKind(JavaKind.Int), BarrierType.NONE));
                read.setGuard(AbstractBeginNode.prevBegin(read));
                return true;
            }
        });
    }

    class InlineAll implements InlineInvokePlugin {
        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
            return createStandardInlineInfo(method);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void test() {
        ResolvedJavaMethod testMethod = getResolvedJavaMethod(PEGraphDecoderTest.class, "doTest", Object.class);
        StructuredGraph targetGraph = null;
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope scope = debug.scope("GraphPETest", testMethod)) {
            GraphBuilderConfiguration graphBuilderConfig = GraphBuilderConfiguration.getDefault(getDefaultGraphBuilderPlugins()).withEagerResolving(true).withUnresolvedIsError(true);
            registerPlugins(graphBuilderConfig.getPlugins().getInvocationPlugins());
            targetGraph = new StructuredGraph.Builder(getInitialOptions(), debug, AllowAssumptions.YES).method(testMethod).build();
            CachingPEGraphDecoder decoder = new CachingPEGraphDecoder(getTarget().arch, targetGraph, getProviders(), graphBuilderConfig, OptimisticOptimizations.NONE, AllowAssumptions.YES,
                            null, null, new InlineInvokePlugin[]{new InlineAll()}, null, null, null, null, null);

            decoder.decode(testMethod, false, false);
            debug.dump(DebugContext.BASIC_LEVEL, targetGraph, "Target Graph");
            targetGraph.verify();

            CoreProviders context = getProviders();
            new CanonicalizerPhase().apply(targetGraph, context);
            targetGraph.verify();

        } catch (Throwable ex) {
            if (targetGraph != null) {
                debug.dump(DebugContext.BASIC_LEVEL, targetGraph, ex.toString());
            }
            debug.handle(ex);
        }
    }
}
