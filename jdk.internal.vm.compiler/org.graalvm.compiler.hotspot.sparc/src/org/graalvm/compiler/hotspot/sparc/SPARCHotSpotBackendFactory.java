/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.sparc;

import java.util.HashSet;
import java.util.Set;

import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.sparc.SPARCAddressLowering;
import org.graalvm.compiler.core.sparc.SPARCSuitesCreator;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.HotSpotBackendFactory;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.HotSpotReplacementsImpl;
import org.graalvm.compiler.hotspot.meta.AddressLoweringHotSpotSuitesProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotConstantFieldProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotGCProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotGraphBuilderPlugins;
import org.graalvm.compiler.hotspot.meta.HotSpotLoweringProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.meta.HotSpotRegisters;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotSnippetReflectionProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotStampProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotSuitesProvider;
import org.graalvm.compiler.hotspot.word.HotSpotWordTypes;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.sparc.SPARCGraphBuilderPlugins;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.runtime.JVMCIBackend;
import jdk.vm.ci.sparc.SPARC;

@ServiceProvider(HotSpotBackendFactory.class)
public class SPARCHotSpotBackendFactory extends HotSpotBackendFactory {

    @Override
    public String getName() {
        return "community";
    }

    @Override
    public Class<? extends Architecture> getArchitecture() {
        return SPARC.class;
    }

    @Override
    public HotSpotBackend createBackend(HotSpotGraalRuntimeProvider runtime, CompilerConfiguration compilerConfiguration, HotSpotJVMCIRuntime jvmciRuntime, HotSpotBackend host) {
        assert host == null;

        GraalHotSpotVMConfig config = runtime.getVMConfig();
        JVMCIBackend jvmci = jvmciRuntime.getHostJVMCIBackend();
        HotSpotRegistersProvider registers = createRegisters();
        HotSpotMetaAccessProvider metaAccess = (HotSpotMetaAccessProvider) jvmci.getMetaAccess();
        HotSpotCodeCacheProvider codeCache = (HotSpotCodeCacheProvider) jvmci.getCodeCache();
        TargetDescription target = codeCache.getTarget();
        HotSpotConstantReflectionProvider constantReflection = (HotSpotConstantReflectionProvider) jvmci.getConstantReflection();
        HotSpotConstantFieldProvider constantFieldProvider = createConstantFieldProvider(config, metaAccess);
        Value[] nativeABICallerSaveRegisters = createNativeABICallerSaveRegisters(config, codeCache.getRegisterConfig());
        HotSpotWordTypes wordTypes = createWordTypes(metaAccess, target);
        HotSpotForeignCallsProvider foreignCalls = new SPARCHotSpotForeignCallsProvider(jvmciRuntime, runtime, metaAccess, codeCache, wordTypes, nativeABICallerSaveRegisters);
        LoweringProvider lowerer = createLowerer(runtime, metaAccess, foreignCalls, registers, constantReflection, target);
        HotSpotStampProvider stampProvider = createStampProvider();
        HotSpotGCProvider gc = createGCProvider(config);
        Providers p = new Providers(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, null, stampProvider, gc);
        HotSpotSnippetReflectionProvider snippetReflection = createSnippetReflection(runtime, constantReflection, wordTypes);
        BytecodeProvider bytecodeProvider = createBytecodeProvider(metaAccess, snippetReflection);
        HotSpotReplacementsImpl replacements = createReplacements(target, p, snippetReflection, bytecodeProvider);
        Plugins plugins = createGraphBuilderPlugins(compilerConfiguration, config, metaAccess, constantReflection, foreignCalls, snippetReflection, replacements, wordTypes, runtime.getOptions());
        replacements.setGraphBuilderPlugins(plugins);
        HotSpotSuitesProvider suites = createSuites(config, runtime, compilerConfiguration, plugins, replacements);
        HotSpotProviders providers = new HotSpotProviders(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, suites, registers,
                        snippetReflection, wordTypes, plugins, gc);
        replacements.setProviders(providers);

        return createBackend(config, runtime, providers);
    }

    protected Plugins createGraphBuilderPlugins(CompilerConfiguration compilerConfiguration, GraalHotSpotVMConfig config, HotSpotMetaAccessProvider metaAccess,
                    HotSpotConstantReflectionProvider constantReflection, HotSpotForeignCallsProvider foreignCalls,
                    HotSpotSnippetReflectionProvider snippetReflection, HotSpotReplacementsImpl replacements, HotSpotWordTypes wordTypes, OptionValues options) {
        Plugins plugins = HotSpotGraphBuilderPlugins.create(compilerConfiguration, config, wordTypes, metaAccess, constantReflection, snippetReflection, foreignCalls, replacements, options);
        SPARCGraphBuilderPlugins.register(plugins, replacements.getDefaultReplacementBytecodeProvider(), false);
        return plugins;
    }

    /**
     * @param replacements
     */
    protected HotSpotSuitesProvider createSuites(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, CompilerConfiguration compilerConfiguration, Plugins plugins,
                    Replacements replacements) {
        return new AddressLoweringHotSpotSuitesProvider(new SPARCSuitesCreator(compilerConfiguration, plugins), config, runtime, new AddressLoweringPhase(new SPARCAddressLowering()));
    }

    protected SPARCHotSpotBackend createBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        return new SPARCHotSpotBackend(config, runtime, providers);
    }

    protected HotSpotLoweringProvider createLowerer(HotSpotGraalRuntimeProvider runtime, HotSpotMetaAccessProvider metaAccess, HotSpotForeignCallsProvider foreignCalls,
                    HotSpotRegistersProvider registers, HotSpotConstantReflectionProvider constantReflection, TargetDescription target) {
        return new SPARCHotSpotLoweringProvider(runtime, metaAccess, foreignCalls, registers, constantReflection, target);
    }

    protected HotSpotRegistersProvider createRegisters() {
        return new HotSpotRegisters(SPARC.g2, SPARC.g6, SPARC.sp);
    }

    @SuppressWarnings("unused")
    private static Value[] createNativeABICallerSaveRegisters(GraalHotSpotVMConfig config, RegisterConfig regConfig) {
        Set<Register> callerSavedRegisters = new HashSet<>();
        SPARC.fpusRegisters.addTo(callerSavedRegisters);
        SPARC.fpudRegisters.addTo(callerSavedRegisters);
        callerSavedRegisters.add(SPARC.g1);
        callerSavedRegisters.add(SPARC.g4);
        callerSavedRegisters.add(SPARC.g5);
        Value[] nativeABICallerSaveRegisters = new Value[callerSavedRegisters.size()];
        int i = 0;
        for (Register reg : callerSavedRegisters) {
            nativeABICallerSaveRegisters[i] = reg.asValue();
            i++;
        }
        return nativeABICallerSaveRegisters;
    }

    @Override
    public String toString() {
        return "SPARC";
    }
}
