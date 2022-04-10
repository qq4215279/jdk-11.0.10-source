/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.FilteredNodeIterable;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.junit.Assert;
import org.junit.Test;

public class CountedLoopTest2 extends GraalCompilerTest {
    public static float countedDeoptLoop0(int n) {
        float v = 0;
        for (int i = 0; i < n; i++) {
            v += 2.1f * i;
            GraalDirectives.controlFlowAnchor();
        }
        GraalDirectives.deoptimizeAndInvalidate();
        return v;
    }

    @Test
    public void test0() {
        test("countedDeoptLoop0");
    }

    public static float countedDeoptLoop1(int n) {
        float v = 0;
        for (int i = 0; i < n; i++) {
            v += 2.1f * i;
            GraalDirectives.controlFlowAnchor();
        }
        if (v > 0) {
            if (v / 55 < 3) {
                v -= 2;
                GraalDirectives.controlFlowAnchor();
            } else {
                v += 6;
                GraalDirectives.controlFlowAnchor();
            }
        } else {
            v += 1;
            GraalDirectives.controlFlowAnchor();
        }
        GraalDirectives.deoptimizeAndInvalidate();
        return v;
    }

    @Test
    public void test1() {
        test("countedDeoptLoop1");
    }

    public static float countedDeoptLoop2(int n, float init) {
        float v = init;
        if (v > 0) {
            if (v / 55 < 3) {
                for (int i = 0; i < n; i++) {
                    v += 2.1f * i;
                    GraalDirectives.controlFlowAnchor();
                }
            } else {
                for (int i = 0; i < n; i++) {
                    v += 1.1f * i;
                    GraalDirectives.controlFlowAnchor();
                }
            }
        } else {
            for (int i = 0; i < n; i++) {
                v += -0.1f * i;
                GraalDirectives.controlFlowAnchor();
            }
        }
        GraalDirectives.deoptimizeAndInvalidate();
        return v;
    }

    @Test
    public void test2() {
        test("countedDeoptLoop2", 3);
    }

    private void test(String methodName) {
        test(methodName, 1);
    }

    private void test(String methodName, int nLoops) {
        StructuredGraph graph = parseEager(methodName, AllowAssumptions.YES);
        LoopsData loops = new LoopsData(graph);
        Assert.assertEquals(nLoops, loops.loops().size());
        for (LoopEx loop : loops.loops()) {
            Assert.assertTrue(loop.detectCounted());
        }

        StructuredGraph finalGraph = getFinalGraph(methodName);
        loops = new LoopsData(finalGraph);
        Assert.assertEquals(nLoops, loops.loops().size());
        FilteredNodeIterable<Node> nonStartDeopts = finalGraph.getNodes().filter(n -> {
            return n instanceof DeoptimizingNode.DeoptBefore && ((DeoptimizingNode.DeoptBefore) n).stateBefore().bci > 0;
        });
        Assert.assertTrue(nonStartDeopts.isNotEmpty());
    }
}
