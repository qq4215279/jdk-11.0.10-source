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


package org.graalvm.compiler.nodes;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.extended.AnchoringNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(allowedUsageTypes = {InputType.Guard, InputType.Anchor})
public abstract class AbstractBeginNode extends FixedWithNextNode implements LIRLowerable, GuardingNode, AnchoringNode, IterableNodeType {

    public static final NodeClass<AbstractBeginNode> TYPE = NodeClass.create(AbstractBeginNode.class);

    private boolean withSpeculationFence;

    protected AbstractBeginNode(NodeClass<? extends AbstractBeginNode> c) {
        this(c, StampFactory.forVoid());
    }

    protected AbstractBeginNode(NodeClass<? extends AbstractBeginNode> c, Stamp stamp) {
        super(c, stamp);
    }

    public static AbstractBeginNode prevBegin(FixedNode from) {
        Node next = from;
        while (next != null) {
            if (next instanceof AbstractBeginNode) {
                return (AbstractBeginNode) next;
            }
            next = next.predecessor();
        }
        return null;
    }

    private void evacuateAnchored(FixedNode evacuateFrom) {
        if (!hasNoUsages()) {
            AbstractBeginNode prevBegin = prevBegin(evacuateFrom);
            assert prevBegin != null;
            replaceAtUsages(InputType.Anchor, prevBegin);
            replaceAtUsages(InputType.Guard, prevBegin);
            assert anchored().isEmpty() : anchored().snapshot();
        }
    }

    public void prepareDelete() {
        prepareDelete((FixedNode) predecessor());
    }

    public void prepareDelete(FixedNode evacuateFrom) {
        evacuateAnchored(evacuateFrom);
    }

    @Override
    public boolean verify() {
        assertTrue(predecessor() != null || this == graph().start() || this instanceof AbstractMergeNode, "begin nodes must be connected");
        return super.verify();
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        if (withSpeculationFence) {
            gen.getLIRGeneratorTool().emitSpeculationFence();
        }
    }

    public NodeIterable<GuardNode> guards() {
        return usages().filter(GuardNode.class);
    }

    public NodeIterable<Node> anchored() {
        return usages();
    }

    public NodeIterable<FixedNode> getBlockNodes() {
        return new NodeIterable<FixedNode>() {

            @Override
            public Iterator<FixedNode> iterator() {
                return new BlockNodeIterator(AbstractBeginNode.this);
            }
        };
    }

    /**
     * Set this begin node to be a speculation fence. This will prevent speculative execution of
     * this block.
     */
    public void setWithSpeculationFence() {
        this.withSpeculationFence = true;
    }

    private static class BlockNodeIterator implements Iterator<FixedNode> {

        private FixedNode current;

        BlockNodeIterator(FixedNode next) {
            this.current = next;
        }

        @Override
        public boolean hasNext() {
            return current != null;
        }

        @Override
        public FixedNode next() {
            FixedNode ret = current;
            if (ret == null) {
                throw new NoSuchElementException();
            }
            if (current instanceof FixedWithNextNode) {
                current = ((FixedWithNextNode) current).next();
                if (current instanceof AbstractBeginNode) {
                    current = null;
                }
            } else {
                current = null;
            }
            return ret;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
