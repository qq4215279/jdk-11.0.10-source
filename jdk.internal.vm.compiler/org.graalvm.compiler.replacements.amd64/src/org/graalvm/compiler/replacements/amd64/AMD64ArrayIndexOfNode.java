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


package org.graalvm.compiler.replacements.amd64;

import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_512;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

@NodeInfo(size = SIZE_512, cycles = NodeCycles.CYCLES_UNKNOWN)
public class AMD64ArrayIndexOfNode extends FixedWithNextNode implements LIRLowerable, MemoryAccess {

    public static final NodeClass<AMD64ArrayIndexOfNode> TYPE = NodeClass.create(AMD64ArrayIndexOfNode.class);

    private final JavaKind arrayKind;
    private final JavaKind valueKind;
    private final boolean findTwoConsecutive;

    @Input private ValueNode arrayPointer;
    @Input private ValueNode arrayLength;
    @Input private ValueNode fromIndex;
    @Input private NodeInputList<ValueNode> searchValues;

    @OptionalInput(InputType.Memory) private MemoryNode lastLocationAccess;

    public AMD64ArrayIndexOfNode(@ConstantNodeParameter JavaKind arrayKind, @ConstantNodeParameter JavaKind valueKind, @ConstantNodeParameter boolean findTwoConsecutive,
                    ValueNode arrayPointer, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.arrayKind = arrayKind;
        this.valueKind = valueKind;
        this.findTwoConsecutive = findTwoConsecutive;
        this.arrayPointer = arrayPointer;
        this.arrayLength = arrayLength;
        this.fromIndex = fromIndex;
        this.searchValues = new NodeInputList<>(this, searchValues);
    }

    public AMD64ArrayIndexOfNode(@ConstantNodeParameter JavaKind arrayKind, @ConstantNodeParameter JavaKind valueKind,
                    ValueNode arrayPointer, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        this(arrayKind, valueKind, false, arrayPointer, arrayLength, fromIndex, searchValues);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return NamedLocationIdentity.getArrayLocation(arrayKind);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value[] searchValueOperands = new Value[searchValues.size()];
        for (int i = 0; i < searchValues.size(); i++) {
            searchValueOperands[i] = gen.operand(searchValues.get(i));
        }
        Value result = gen.getLIRGeneratorTool().emitArrayIndexOf(arrayKind, valueKind, findTwoConsecutive,
                        gen.operand(arrayPointer), gen.operand(arrayLength), gen.operand(fromIndex), searchValueOperands);
        gen.setResult(this, result);
    }

    @Override
    public MemoryNode getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryNode lla) {
        updateUsages(ValueNodeUtil.asNode(lastLocationAccess), ValueNodeUtil.asNode(lla));
        lastLocationAccess = lla;
    }

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1, byte v2);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1, byte v2, byte v3);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1, byte v2, byte v3, byte v4);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1, char v2);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1, char v2, char v3);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1, char v2, char v3, char v4);

    @NodeIntrinsic
    private static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, int searchValue);

    public static int indexOf(byte[] array, int arrayLength, int fromIndex, byte v1) {
        return optimizedArrayIndexOf(JavaKind.Byte, JavaKind.Byte, false, array, arrayLength, fromIndex, v1);
    }

    public static int indexOf(byte[] array, int arrayLength, int fromIndex, byte v1, byte v2) {
        return optimizedArrayIndexOf(JavaKind.Byte, JavaKind.Byte, false, array, arrayLength, fromIndex, v1, v2);
    }

    public static int indexOf(byte[] array, int arrayLength, int fromIndex, byte v1, byte v2, byte v3) {
        return optimizedArrayIndexOf(JavaKind.Byte, JavaKind.Byte, false, array, arrayLength, fromIndex, v1, v2, v3);
    }

    public static int indexOf(byte[] array, int arrayLength, int fromIndex, byte v1, byte v2, byte v3, byte v4) {
        return optimizedArrayIndexOf(JavaKind.Byte, JavaKind.Byte, false, array, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    public static int indexOf(byte[] array, int arrayLength, int fromIndex, char v1) {
        return optimizedArrayIndexOf(JavaKind.Byte, JavaKind.Char, false, array, arrayLength, fromIndex, v1);
    }

    public static int indexOf(byte[] array, int arrayLength, int fromIndex, char v1, char v2) {
        return optimizedArrayIndexOf(JavaKind.Byte, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2);
    }

    public static int indexOf(byte[] array, int arrayLength, int fromIndex, char v1, char v2, char v3) {
        return optimizedArrayIndexOf(JavaKind.Byte, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2, v3);
    }

    public static int indexOf(byte[] array, int arrayLength, int fromIndex, char v1, char v2, char v3, char v4) {
        return optimizedArrayIndexOf(JavaKind.Byte, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    public static int indexOf(char[] array, int arrayLength, int fromIndex, char v1) {
        return optimizedArrayIndexOf(JavaKind.Char, JavaKind.Char, false, array, arrayLength, fromIndex, v1);
    }

    public static int indexOf(char[] array, int arrayLength, int fromIndex, char v1, char v2) {
        return optimizedArrayIndexOf(JavaKind.Char, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2);
    }

    public static int indexOf(char[] array, int arrayLength, int fromIndex, char v1, char v2, char v3) {
        return optimizedArrayIndexOf(JavaKind.Char, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2, v3);
    }

    public static int indexOf(char[] array, int arrayLength, int fromIndex, char v1, char v2, char v3, char v4) {
        return optimizedArrayIndexOf(JavaKind.Char, JavaKind.Char, false, array, arrayLength, fromIndex, v1, v2, v3, v4);
    }

    public static int indexOf2ConsecutiveBytes(byte[] array, int arrayLength, int fromIndex, int values) {
        return optimizedArrayIndexOf(JavaKind.Byte, JavaKind.Byte, true, array, arrayLength, fromIndex, values);
    }

    public static int indexOf2ConsecutiveChars(byte[] array, int arrayLength, int fromIndex, int values) {
        return optimizedArrayIndexOf(JavaKind.Byte, JavaKind.Char, true, array, arrayLength, fromIndex, values);
    }

    public static int indexOf2ConsecutiveChars(char[] array, int arrayLength, int fromIndex, int values) {
        return optimizedArrayIndexOf(JavaKind.Char, JavaKind.Char, true, array, arrayLength, fromIndex, values);
    }
}
