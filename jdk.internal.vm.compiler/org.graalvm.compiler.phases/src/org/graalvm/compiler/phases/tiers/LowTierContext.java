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


package org.graalvm.compiler.phases.tiers;

import org.graalvm.compiler.nodes.spi.CoreProvidersDelegate;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.code.TargetDescription;

public class LowTierContext extends CoreProvidersDelegate {

    private final TargetProvider target;

    public LowTierContext(Providers copyFrom, TargetProvider target) {
        super(copyFrom);
        this.target = target;
    }

    public TargetDescription getTarget() {
        return target.getTarget();
    }

    public TargetProvider getTargetProvider() {
        return target;
    }
}
