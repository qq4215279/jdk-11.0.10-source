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


package org.graalvm.compiler.core.phases;

import static org.graalvm.compiler.core.common.GraalOptions.ConditionalElimination;
import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;
import static org.graalvm.compiler.core.common.GraalOptions.LoopPeeling;
import static org.graalvm.compiler.core.common.GraalOptions.LoopUnswitch;
import static org.graalvm.compiler.core.common.GraalOptions.OptConvertDeoptsToGuards;
import static org.graalvm.compiler.core.common.GraalOptions.OptLoopTransform;
import static org.graalvm.compiler.core.common.GraalOptions.OptReadElimination;
import static org.graalvm.compiler.core.common.GraalOptions.PartialEscapeAnalysis;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import org.graalvm.compiler.loop.DefaultLoopPolicies;
import org.graalvm.compiler.loop.LoopPolicies;
import org.graalvm.compiler.loop.phases.LoopFullUnrollPhase;
import org.graalvm.compiler.loop.phases.LoopPeelingPhase;
import org.graalvm.compiler.loop.phases.LoopUnswitchingPhase;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.IncrementalCanonicalizerPhase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.NodeCounterPhase;
import org.graalvm.compiler.phases.common.RemoveValueProxyPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.common.inlining.policy.GreedyInliningPolicy;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.virtual.phases.ea.EarlyReadEliminationPhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;

public class HighTier extends PhaseSuite<HighTierContext> {

    public static class Options {

        // @formatter:off
        @Option(help = "Enable inlining", type = OptionType.Expert)
        public static final OptionKey<Boolean> Inline = new OptionKey<>(true);
        // @formatter:on
    }

    public HighTier(OptionValues options) {
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        if (ImmutableCode.getValue(options)) {
            canonicalizer.disableReadCanonicalization();
        }

        appendPhase(canonicalizer);

        if (NodeCounterPhase.Options.NodeCounters.getValue(options)) {
            appendPhase(new NodeCounterPhase(NodeCounterPhase.Stage.INIT));
        }

        if (Options.Inline.getValue(options)) {
            appendPhase(new InliningPhase(new GreedyInliningPolicy(null), canonicalizer));
            appendPhase(new DeadCodeEliminationPhase(Optional));
        }

        if (NodeCounterPhase.Options.NodeCounters.getValue(options)) {
            appendPhase(new NodeCounterPhase(NodeCounterPhase.Stage.EARLY));
        }

        if (OptConvertDeoptsToGuards.getValue(options)) {
            appendPhase(new IncrementalCanonicalizerPhase<>(canonicalizer, new ConvertDeoptimizeToGuardPhase()));
        }

        if (ConditionalElimination.getValue(options)) {
            appendPhase(new IterativeConditionalEliminationPhase(canonicalizer, false));
        }

        LoopPolicies loopPolicies = createLoopPolicies();
        appendPhase(new LoopFullUnrollPhase(canonicalizer, loopPolicies));

        if (OptLoopTransform.getValue(options)) {
            if (LoopPeeling.getValue(options)) {
                appendPhase(new IncrementalCanonicalizerPhase<>(canonicalizer, new LoopPeelingPhase(loopPolicies)));
            }
            if (LoopUnswitch.getValue(options)) {
                appendPhase(new IncrementalCanonicalizerPhase<>(canonicalizer, new LoopUnswitchingPhase(loopPolicies)));
            }
        }

        if (PartialEscapeAnalysis.getValue(options)) {
            appendPhase(new PartialEscapePhase(true, canonicalizer, options));
        }

        if (OptReadElimination.getValue(options)) {
            appendPhase(new EarlyReadEliminationPhase(canonicalizer));
        }

        appendPhase(new RemoveValueProxyPhase());

        if (NodeCounterPhase.Options.NodeCounters.getValue(options)) {
            appendPhase(new NodeCounterPhase(NodeCounterPhase.Stage.LATE));
        }

        appendPhase(new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER));
    }

    public LoopPolicies createLoopPolicies() {
        return new DefaultLoopPolicies();
    }
}
