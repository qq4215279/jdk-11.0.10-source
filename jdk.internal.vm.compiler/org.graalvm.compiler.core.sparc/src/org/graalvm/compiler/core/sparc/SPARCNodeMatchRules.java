/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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



package org.graalvm.compiler.core.sparc;

import static jdk.vm.ci.sparc.SPARCKind.BYTE;
import static jdk.vm.ci.sparc.SPARCKind.HWORD;
import static jdk.vm.ci.sparc.SPARCKind.WORD;
import static jdk.vm.ci.sparc.SPARCKind.XWORD;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.gen.NodeMatchRules;
import org.graalvm.compiler.core.match.ComplexMatchResult;
import org.graalvm.compiler.core.match.MatchRule;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.sparc.SPARCAddressValue;
import org.graalvm.compiler.nodes.DeoptimizingNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.CompareNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.java.LogicCompareAndSwapNode;
import org.graalvm.compiler.nodes.memory.Access;
import org.graalvm.compiler.nodes.memory.LIRLowerableAccess;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.sparc.SPARCKind;

/**
 * This class implements the SPARC specific portion of the LIR generator.
 */
public class SPARCNodeMatchRules extends NodeMatchRules {

    public SPARCNodeMatchRules(LIRGeneratorTool gen) {
        super(gen);
    }

    protected LIRFrameState getState(Access access) {
        if (access instanceof DeoptimizingNode) {
            return state((DeoptimizingNode) access);
        }
        return null;
    }

    protected LIRKind getLirKind(LIRLowerableAccess access) {
        return gen.getLIRKind(access.getAccessStamp());
    }

    private ComplexMatchResult emitSignExtendMemory(Access access, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        SPARCKind toKind = null;
        SPARCKind fromKind = null;
        if (fromBits == toBits) {
            return null;
        }
        toKind = toBits > 32 ? XWORD : WORD;
        switch (fromBits) {
            case 8:
                fromKind = BYTE;
                break;
            case 16:
                fromKind = HWORD;
                break;
            case 32:
                fromKind = WORD;
                break;
            default:
                throw GraalError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
        }
        SPARCKind localFromKind = fromKind;
        SPARCKind localToKind = toKind;
        return builder -> {
            return getLIRGeneratorTool().emitSignExtendLoad(LIRKind.value(localFromKind), LIRKind.value(localToKind), operand(access.getAddress()), getState(access));
        };
    }

    private ComplexMatchResult emitZeroExtendMemory(Access access, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        SPARCKind toKind = null;
        SPARCKind fromKind = null;
        if (fromBits == toBits) {
            return null;
        }
        toKind = toBits > 32 ? XWORD : WORD;
        switch (fromBits) {
            case 8:
                fromKind = BYTE;
                break;
            case 16:
                fromKind = HWORD;
                break;
            case 32:
                fromKind = WORD;
                break;
            default:
                throw GraalError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
        }
        SPARCKind localFromKind = fromKind;
        SPARCKind localToKind = toKind;
        return builder -> {
            // Loads are always zero extending load
            return getLIRGeneratorTool().emitZeroExtendLoad(LIRKind.value(localFromKind), LIRKind.value(localToKind), operand(access.getAddress()), getState(access));
        };
    }

    @MatchRule("(SignExtend Read=access)")
    @MatchRule("(SignExtend FloatingRead=access)")
    public ComplexMatchResult signExtend(SignExtendNode root, Access access) {
        return emitSignExtendMemory(access, root.getInputBits(), root.getResultBits());
    }

    @MatchRule("(ZeroExtend Read=access)")
    @MatchRule("(ZeroExtend FloatingRead=access)")
    public ComplexMatchResult zeroExtend(ZeroExtendNode root, Access access) {
        return emitZeroExtendMemory(access, root.getInputBits(), root.getResultBits());
    }

    @MatchRule("(If (ObjectEquals=compare value LogicCompareAndSwap=cas))")
    @MatchRule("(If (PointerEquals=compare value LogicCompareAndSwap=cas))")
    @MatchRule("(If (FloatEquals=compare value LogicCompareAndSwap=cas))")
    @MatchRule("(If (IntegerEquals=compare value LogicCompareAndSwap=cas))")
    public ComplexMatchResult ifCompareLogicCas(IfNode root, CompareNode compare, ValueNode value, LogicCompareAndSwapNode cas) {
        JavaConstant constant = value.asJavaConstant();
        assert compare.condition() == CanonicalCondition.EQ;
        if (constant != null && cas.hasExactlyOneUsage()) {
            long constantValue = constant.asLong();
            boolean successIsTrue;
            if (constantValue == 0) {
                successIsTrue = false;
            } else if (constantValue == 1) {
                successIsTrue = true;
            } else {
                return null;
            }
            return builder -> {
                LIRKind kind = getLirKind(cas);
                LabelRef trueLabel = getLIRBlock(root.trueSuccessor());
                LabelRef falseLabel = getLIRBlock(root.falseSuccessor());
                double trueLabelProbability = root.probability(root.trueSuccessor());
                Value expectedValue = operand(cas.getExpectedValue());
                Value newValue = operand(cas.getNewValue());
                SPARCAddressValue address = (SPARCAddressValue) operand(cas.getAddress());
                Condition condition = successIsTrue ? Condition.EQ : Condition.NE;

                Value result = getLIRGeneratorTool().emitValueCompareAndSwap(kind, address, expectedValue, newValue);
                getLIRGeneratorTool().emitCompareBranch(kind.getPlatformKind(), result, expectedValue, condition, false, trueLabel, falseLabel, trueLabelProbability);
                return null;
            };
        }
        return null;
    }

    @Override
    public SPARCLIRGenerator getLIRGeneratorTool() {
        return (SPARCLIRGenerator) super.getLIRGeneratorTool();
    }

    protected SPARCArithmeticLIRGenerator getArithmeticLIRGenerator() {
        return (SPARCArithmeticLIRGenerator) getLIRGeneratorTool().getArithmetic();
    }
}
