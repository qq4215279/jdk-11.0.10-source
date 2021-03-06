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


package org.graalvm.compiler.hotspot.stubs;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.api.replacements.Snippet.NonNullParameter;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.GuardsStage;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.RemoveValueProxyPhase;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.Snippets;

import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Base class for a stub defined by a snippet.
 */
public abstract class SnippetStub extends Stub implements Snippets {

    protected final ResolvedJavaMethod method;

    /**
     * Creates a new snippet stub.
     *
     * @param snippetMethodName name of the single {@link Snippet} annotated method in the class of
     *            this object
     * @param linkage linkage details for a call to the stub
     */
    public SnippetStub(String snippetMethodName, OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        this(null, snippetMethodName, options, providers, linkage);
    }

    /**
     * Creates a new snippet stub.
     *
     * @param snippetDeclaringClass this class in which the {@link Snippet} annotated method is
     *            declared. If {@code null}, this the class of this object is used.
     * @param snippetMethodName name of the single {@link Snippet} annotated method in
     *            {@code snippetDeclaringClass}
     * @param linkage linkage details for a call to the stub
     */
    public SnippetStub(Class<? extends Snippets> snippetDeclaringClass, String snippetMethodName, OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(options, providers, linkage);
        this.method = SnippetTemplate.AbstractTemplates.findMethod(providers.getMetaAccess(), snippetDeclaringClass == null ? getClass() : snippetDeclaringClass, snippetMethodName);
        registerSnippet();
    }

    protected void registerSnippet() {
        providers.getReplacements().registerSnippet(method, null, null, false, options);
    }

    @Override
    @SuppressWarnings("try")
    protected StructuredGraph getGraph(DebugContext debug, CompilationIdentifier compilationId) {
        // Stubs cannot have optimistic assumptions since they have
        // to be valid for the entire run of the VM.
        final StructuredGraph graph = buildInitialGraph(debug, compilationId, makeConstArgs());
        try (DebugContext.Scope outer = debug.scope("SnippetStub", graph)) {
            for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                int index = param.index();
                if (method.getParameterAnnotation(NonNullParameter.class, index) != null) {
                    param.setStamp(param.stamp(NodeView.DEFAULT).join(StampFactory.objectNonNull()));
                }
            }

            new RemoveValueProxyPhase().apply(graph);
            graph.setGuardsStage(GuardsStage.FLOATING_GUARDS);
            CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
            canonicalizer.apply(graph, providers);
            new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, providers);
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        return graph;
    }

    protected StructuredGraph buildInitialGraph(DebugContext debug, CompilationIdentifier compilationId, Object[] args) {
        return providers.getReplacements().getSnippet(method, null, args, false, null, options).copyWithIdentifier(compilationId, debug);
    }

    protected boolean checkConstArg(int index, String expectedName) {
        assert method.getParameterAnnotation(ConstantParameter.class, index) != null : String.format("parameter %d of %s is expected to be constant", index, method.format("%H.%n(%p)"));
        LocalVariableTable lvt = method.getLocalVariableTable();
        if (lvt != null) {
            Local local = lvt.getLocal(index, 0);
            assert local != null;
            String actualName = local.getName();
            assert actualName.equals(expectedName) : String.format("parameter %d of %s is expected to be named %s, not %s", index, method.format("%H.%n(%p)"), expectedName, actualName);
        }
        return true;
    }

    protected Object[] makeConstArgs() {
        int count = method.getSignature().getParameterCount(false);
        Object[] args = new Object[count];
        for (int i = 0; i < args.length; i++) {
            if (method.getParameterAnnotation(ConstantParameter.class, i) != null) {
                args[i] = getConstantParameterValue(i, null);
            }
        }
        return args;
    }

    protected Object getConstantParameterValue(int index, String name) {
        throw new GraalError("%s must override getConstantParameterValue() to provide a value for parameter %d%s", getClass().getName(), index, name == null ? "" : " (" + name + ")");
    }

    @Override
    protected Object debugScopeContext() {
        return getInstalledCodeOwner();
    }

    @Override
    public ResolvedJavaMethod getInstalledCodeOwner() {
        return method;
    }

    @Override
    public String toString() {
        return "Stub<" + getInstalledCodeOwner().format("%h.%n") + ">";
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }
}
