/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToNode;
import org.graalvm.compiler.replacements.nodes.ArrayRegionEqualsNode;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.Pointer;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

// JaCoCo Exclude

/**
 * Substitutions for {@code java.lang.StringLatin1} methods.
 *
 * Since JDK 9.
 */
@ClassSubstitution(className = "java.lang.StringLatin1", optional = true)
public class AMD64StringLatin1Substitutions {

    @Fold
    static int byteArrayBaseOffset(@InjectedParameter MetaAccessProvider metaAccess) {
        return metaAccess.getArrayBaseOffset(JavaKind.Byte);
    }

    @Fold
    static int byteArrayIndexScale(@InjectedParameter MetaAccessProvider metaAccess) {
        return metaAccess.getArrayIndexScale(JavaKind.Byte);
    }

    @Fold
    static int charArrayBaseOffset(@InjectedParameter MetaAccessProvider metaAccess) {
        return metaAccess.getArrayBaseOffset(JavaKind.Char);
    }

    @Fold
    static int charArrayIndexScale(@InjectedParameter MetaAccessProvider metaAccess) {
        return metaAccess.getArrayIndexScale(JavaKind.Char);
    }

    /** Marker value for the {@link InjectedParameter} injected parameter. */
    static final MetaAccessProvider INJECTED = null;

    /**
     * @param value is byte[]
     * @param other is byte[]
     */
    @MethodSubstitution
    public static int compareTo(byte[] value, byte[] other) {
        return ArrayCompareToNode.compareTo(value, other, value.length, other.length, JavaKind.Byte, JavaKind.Byte);
    }

    /**
     * @param value is byte[]
     * @param other is char[]
     */
    @MethodSubstitution
    public static int compareToUTF16(byte[] value, byte[] other) {
        return ArrayCompareToNode.compareTo(value, other, value.length, other.length, JavaKind.Byte, JavaKind.Char);
    }

    private static Word pointer(byte[] target) {
        return Word.objectToTrackedPointer(target).add(byteArrayBaseOffset(INJECTED));
    }

    private static Word byteOffsetPointer(byte[] source, int offset) {
        return pointer(source).add(offset * byteArrayIndexScale(INJECTED));
    }

    @MethodSubstitution
    public static int indexOf(byte[] value, int ch, int origFromIndex) {
        int fromIndex = origFromIndex;
        if (ch >>> 8 != 0) {
            // search value must be a byte value
            return -1;
        }
        int length = value.length;
        if (fromIndex < 0) {
            fromIndex = 0;
        } else if (fromIndex >= length) {
            // Note: fromIndex might be near -1>>>1.
            return -1;
        }
        return AMD64ArrayIndexOf.indexOf1Byte(value, length, fromIndex, (byte) ch);
    }

    @MethodSubstitution
    public static int indexOf(byte[] source, int sourceCount, byte[] target, int targetCount, int origFromIndex) {
        int fromIndex = origFromIndex;
        if (fromIndex >= sourceCount) {
            return (targetCount == 0 ? sourceCount : -1);
        }
        if (fromIndex < 0) {
            fromIndex = 0;
        }
        if (targetCount == 0) {
            // The empty string is in every string.
            return fromIndex;
        }
        if (sourceCount - fromIndex < targetCount) {
            // The empty string contains nothing except the empty string.
            return -1;
        }
        if (targetCount == 1) {
            return AMD64ArrayIndexOf.indexOf1Byte(source, sourceCount, fromIndex, target[0]);
        } else if (targetCount == 2) {
            return AMD64ArrayIndexOf.indexOfTwoConsecutiveBytes(source, sourceCount, fromIndex, target[0], target[1]);
        } else {
            int haystackLength = sourceCount - (targetCount - 2);
            int offset = fromIndex;
            while (offset < haystackLength) {
                int indexOfResult = AMD64ArrayIndexOf.indexOfTwoConsecutiveBytes(source, haystackLength, offset, target[0], target[1]);
                if (indexOfResult < 0) {
                    return -1;
                }
                offset = indexOfResult;
                Pointer cmpSourcePointer = byteOffsetPointer(source, offset);
                Pointer targetPointer = pointer(target);
                if (ArrayRegionEqualsNode.regionEquals(cmpSourcePointer, targetPointer, targetCount, JavaKind.Byte)) {
                    return offset;
                }
                offset++;
            }
            return -1;
        }
    }

    /**
     * Intrinsic for {@code java.lang.StringLatin1.inflate([BI[CII)V}.
     *
     * <pre>
     * &#64;HotSpotIntrinsicCandidate
     * public static void inflate(byte[] src, int src_indx, char[] dst, int dst_indx, int len)
     * </pre>
     */
    @MethodSubstitution
    public static void inflate(byte[] src, int srcIndex, char[] dest, int destIndex, int len) {
        if (len < 0 || srcIndex < 0 || (srcIndex + len > src.length) || destIndex < 0 || (destIndex + len > dest.length)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
        }

        // Offset calc. outside of the actual intrinsic.
        Pointer srcPointer = Word.objectToTrackedPointer(src).add(byteArrayBaseOffset(INJECTED)).add(srcIndex * byteArrayIndexScale(INJECTED));
        Pointer destPointer = Word.objectToTrackedPointer(dest).add(charArrayBaseOffset(INJECTED)).add(destIndex * charArrayIndexScale(INJECTED));
        AMD64StringLatin1InflateNode.inflate(srcPointer, destPointer, len, JavaKind.Char);
    }

    /**
     * Intrinsic for {@code }java.lang.StringLatin1.inflate([BI[BII)V}.
     *
     * <pre>
     * &#64;HotSpotIntrinsicCandidate
     * public static void inflate(byte[] src, int src_indx, byte[] dst, int dst_indx, int len)
     * </pre>
     *
     * In this variant {@code dest} refers to a byte array containing 2 byte per char so
     * {@code destIndex} and {@code len} are in terms of char elements and have to be scaled by 2
     * when referring to {@code dest}
     */
    @MethodSubstitution
    public static void inflate(byte[] src, int srcIndex, byte[] dest, int destIndex, int len) {
        if (len < 0 || srcIndex < 0 || (srcIndex + len > src.length) || destIndex < 0 || (destIndex * 2 + len * 2 > dest.length)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
        }

        // Offset calc. outside of the actual intrinsic.
        Pointer srcPointer = Word.objectToTrackedPointer(src).add(byteArrayBaseOffset(INJECTED)).add(srcIndex * byteArrayIndexScale(INJECTED));
        Pointer destPointer = Word.objectToTrackedPointer(dest).add(byteArrayBaseOffset(INJECTED)).add(destIndex * 2 * byteArrayIndexScale(INJECTED));
        AMD64StringLatin1InflateNode.inflate(srcPointer, destPointer, len, JavaKind.Byte);
    }

}
