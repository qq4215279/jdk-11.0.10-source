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

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.replacements.StringSubstitutions;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToNode;
import org.graalvm.compiler.replacements.nodes.ArrayRegionEqualsNode;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.Pointer;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

// JaCoCo Exclude

/**
 * Substitutions for {@link java.lang.String} methods.
 */
@ClassSubstitution(String.class)
public class AMD64StringSubstitutions {

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

    // Only exists in JDK <= 8
    @MethodSubstitution(isStatic = true, optional = true)
    public static int indexOf(char[] source, int sourceOffset, int sourceCount,
                    @ConstantNodeParameter char[] target, int targetOffset, int targetCount,
                    int origFromIndex) {
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

        int totalOffset = sourceOffset + fromIndex;
        if (sourceCount - fromIndex < targetCount) {
            // The empty string contains nothing except the empty string.
            return -1;
        }

        if (targetCount == 1) {
            return AMD64ArrayIndexOf.indexOf1Char(source, sourceCount, totalOffset, target[targetOffset]);
        } else if (targetCount == 2) {
            return AMD64ArrayIndexOf.indexOfTwoConsecutiveChars(source, sourceCount, totalOffset, target[targetOffset], target[targetOffset + 1]);
        } else {
            int haystackLength = sourceCount - (targetCount - 2);
            while (totalOffset < haystackLength) {
                int indexOfResult = AMD64ArrayIndexOf.indexOfTwoConsecutiveChars(source, haystackLength, totalOffset, target[targetOffset], target[targetOffset + 1]);
                if (indexOfResult < 0) {
                    return -1;
                }
                totalOffset = indexOfResult;
                Pointer cmpSourcePointer = Word.objectToTrackedPointer(source).add(charArrayBaseOffset(INJECTED)).add(totalOffset * charArrayIndexScale(INJECTED));
                Pointer targetPointer = Word.objectToTrackedPointer(target).add(charArrayBaseOffset(INJECTED)).add(targetOffset * charArrayIndexScale(INJECTED));
                if (ArrayRegionEqualsNode.regionEquals(cmpSourcePointer, targetPointer, targetCount, JavaKind.Char)) {
                    return totalOffset;
                }
                totalOffset++;
            }
            return -1;
        }
    }

    // Only exists in JDK <= 8
    @MethodSubstitution(isStatic = false, optional = true)
    public static int indexOf(String source, int ch, int origFromIndex) {
        int fromIndex = origFromIndex;
        final int sourceCount = source.length();
        if (fromIndex >= sourceCount) {
            // Note: fromIndex might be near -1>>>1.
            return -1;
        }
        if (fromIndex < 0) {
            fromIndex = 0;
        }

        if (ch < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
            char[] sourceArray = StringSubstitutions.getValue(source);
            return AMD64ArrayIndexOf.indexOf1Char(sourceArray, sourceCount, fromIndex, (char) ch);
        } else {
            return indexOf(source, ch, origFromIndex);
        }
    }

    @MethodSubstitution(isStatic = false)
    @SuppressFBWarnings(value = "ES_COMPARING_PARAMETER_STRING_WITH_EQ", justification = "reference equality on the receiver is what we want")
    public static int compareTo(String receiver, String anotherString) {
        if (receiver == anotherString) {
            return 0;
        }
        char[] value = StringSubstitutions.getValue(receiver);
        char[] other = StringSubstitutions.getValue(anotherString);
        return ArrayCompareToNode.compareTo(value, other, value.length << 1, other.length << 1, JavaKind.Char, JavaKind.Char);
    }

}
