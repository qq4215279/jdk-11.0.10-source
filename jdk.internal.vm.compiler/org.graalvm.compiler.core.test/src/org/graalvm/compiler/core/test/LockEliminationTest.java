/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import org.graalvm.compiler.loop.DefaultLoopPolicies;
import org.graalvm.compiler.loop.phases.LoopFullUnrollPhase;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.java.MonitorExitNode;
import org.graalvm.compiler.nodes.java.RawMonitorEnterNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.LockEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class LockEliminationTest extends GraalCompilerTest {

    static class A {

        int value;

        public synchronized int getValue() {
            return value;
        }
    }

    static int field1;
    static int field2;

    public static void testSynchronizedSnippet(A x, A y) {
        synchronized (x) {
            field1 = x.value;
        }
        synchronized (x) {
            field2 = y.value;
        }
    }

    @Test
    public void testLock() {
        test("testSynchronizedSnippet", new A(), new A());

        StructuredGraph graph = getGraph("testSynchronizedSnippet", false);
        new CanonicalizerPhase().apply(graph, getProviders());
        new LockEliminationPhase().apply(graph);
        assertDeepEquals(1, graph.getNodes().filter(RawMonitorEnterNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(MonitorExitNode.class).count());
    }

    public static void testSynchronizedMethodSnippet(A x) {
        int value1 = x.getValue();
        int value2 = x.getValue();
        field1 = value1;
        field2 = value2;
    }

    @Test
    public void testSynchronizedMethod() {
        test("testSynchronizedMethodSnippet", new A());

        StructuredGraph graph = getGraph("testSynchronizedMethodSnippet", false);
        new CanonicalizerPhase().apply(graph, getProviders());
        new LockEliminationPhase().apply(graph);
        assertDeepEquals(1, graph.getNodes().filter(RawMonitorEnterNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(MonitorExitNode.class).count());
    }

    public void testUnrolledSyncSnippet(Object a) {
        for (int i = 0; i < 3; i++) {
            synchronized (a) {

            }
        }
    }

    @Test
    public void testUnrolledSync() {
        StructuredGraph graph = getGraph("testUnrolledSyncSnippet", false);
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        canonicalizer.apply(graph, getProviders());
        HighTierContext context = getDefaultHighTierContext();
        new LoopFullUnrollPhase(canonicalizer, new DefaultLoopPolicies()).apply(graph, context);
        new LockEliminationPhase().apply(graph);
        assertDeepEquals(1, graph.getNodes().filter(RawMonitorEnterNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(MonitorExitNode.class).count());
    }

    private StructuredGraph getGraph(String snippet, boolean doEscapeAnalysis) {
        ResolvedJavaMethod method = getResolvedJavaMethod(snippet);
        StructuredGraph graph = parseEager(method, AllowAssumptions.YES);
        HighTierContext context = getDefaultHighTierContext();
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        canonicalizer.apply(graph, context);
        createInliningPhase().apply(graph, context);
        new CanonicalizerPhase().apply(graph, context);
        new DeadCodeEliminationPhase().apply(graph);
        if (doEscapeAnalysis) {
            new PartialEscapePhase(true, canonicalizer, graph.getOptions()).apply(graph, context);
        }
        new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        return graph;
    }

    public void testEscapeAnalysisSnippet(A a) {
        A newA = new A();
        synchronized (newA) {
            synchronized (a) {
                field1 = a.value;
            }
        }
        /*
         * Escape analysis removes the synchronization on newA. But lock elimination still must not
         * combine the two synchronizations on the parameter a because they have a different lock
         * depth.
         */
        synchronized (a) {
            field2 = a.value;
        }
        /*
         * Lock elimination can combine these synchronizations, since they are both on parameter a
         * with the same lock depth.
         */
        synchronized (a) {
            field1 = a.value;
        }
    }

    @Test
    public void testEscapeAnalysis() {
        StructuredGraph graph = getGraph("testEscapeAnalysisSnippet", true);

        assertDeepEquals(3, graph.getNodes().filter(RawMonitorEnterNode.class).count());
        assertDeepEquals(3, graph.getNodes().filter(MonitorExitNode.class).count());

        new LockEliminationPhase().apply(graph);

        assertDeepEquals(2, graph.getNodes().filter(RawMonitorEnterNode.class).count());
        assertDeepEquals(2, graph.getNodes().filter(MonitorExitNode.class).count());
    }
}
