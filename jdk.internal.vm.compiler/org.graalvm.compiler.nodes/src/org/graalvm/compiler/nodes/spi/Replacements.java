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


package org.graalvm.compiler.nodes.spi;

import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.api.replacements.SnippetTemplateCache;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.Cancellable;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.MethodSubstitutionPlugin;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Interface for managing replacements.
 */
public interface Replacements {

    CoreProviders getProviders();

    /**
     * Gets the object managing the various graph builder plugins used by this object when parsing
     * bytecode into a graph.
     */
    GraphBuilderConfiguration.Plugins getGraphBuilderPlugins();

    /**
     * Gets the plugin type that intrinsifies calls to {@code method}.
     */
    Class<? extends GraphBuilderPlugin> getIntrinsifyingPlugin(ResolvedJavaMethod method);

    /**
     * Gets the snippet graph derived from a given method.
     *
     * @param recursiveEntry if the snippet contains a call to this method, it's considered as
     *            recursive call and won't be processed for {@linkplain MethodSubstitution
     *            substitutions}.
     * @param args arguments to the snippet if available, otherwise {@code null}
     * @param trackNodeSourcePosition
     * @param options
     * @return the snippet graph, if any, that is derived from {@code method}
     */
    StructuredGraph getSnippet(ResolvedJavaMethod method, ResolvedJavaMethod recursiveEntry, Object[] args, boolean trackNodeSourcePosition, NodeSourcePosition replaceePosition,
                    OptionValues options);

    /**
     * Registers a method as snippet.
     */
    void registerSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, boolean trackNodeSourcePosition, OptionValues options);

    /**
     * Gets a graph that is a substitution for a given {@link MethodSubstitutionPlugin plugin} in
     * the {@link org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext
     * context}.
     *
     * @param plugin the plugin being substituted
     * @param original the method being substituted
     * @param context the kind of inlining to be performed for the substitution
     * @param allowAssumptions
     * @param cancellable
     * @param options
     * @return the method substitution graph, if any, that is derived from {@code method}
     */
    StructuredGraph getMethodSubstitution(MethodSubstitutionPlugin plugin, ResolvedJavaMethod original, IntrinsicContext.CompilationContext context,
                    StructuredGraph.AllowAssumptions allowAssumptions, Cancellable cancellable, OptionValues options);

    /**
     * Registers a plugin as a substitution.
     */
    void registerMethodSubstitution(MethodSubstitutionPlugin plugin, ResolvedJavaMethod original, IntrinsicContext.CompilationContext context, OptionValues options);

    /**
     * Gets a graph that is a substitution for a given method.
     *
     * @param invokeBci the call site BCI if this request is made for inlining a substitute
     *            otherwise {@code -1}
     * @param trackNodeSourcePosition
     * @param options
     * @return the graph, if any, that is a substitution for {@code method}
     */
    StructuredGraph getSubstitution(ResolvedJavaMethod method, int invokeBci, boolean trackNodeSourcePosition, NodeSourcePosition replaceePosition, OptionValues options);

    /**
     * Gets a graph produced from the intrinsic for a given method that can be compiled and
     * installed for the method.
     *
     * @param method
     * @param compilationId
     * @param debug
     * @param cancellable
     * @return an intrinsic graph that can be compiled and installed for {@code method} or null
     */
    StructuredGraph getIntrinsicGraph(ResolvedJavaMethod method, CompilationIdentifier compilationId, DebugContext debug, Cancellable cancellable);

    /**
     * Determines if there may be a
     * {@linkplain #getSubstitution(ResolvedJavaMethod, int, boolean, NodeSourcePosition, OptionValues)
     * substitution graph} for a given method.
     *
     * A call to {@link #getSubstitution} may still return {@code null} for {@code method} and
     * {@code invokeBci}. A substitution may be based on an {@link InvocationPlugin} that returns
     * {@code false} for {@link InvocationPlugin#execute} making it impossible to create a
     * substitute graph.
     *
     * @param invokeBci the call site BCI if this request is made for inlining a substitute
     *            otherwise {@code -1}
     * @return true iff there may be a substitution graph available for {@code method}
     */
    boolean hasSubstitution(ResolvedJavaMethod method, int invokeBci);

    /**
     * Gets the provider for accessing the bytecode of a substitution method if no other provider is
     * associated with the substitution method.
     */
    BytecodeProvider getDefaultReplacementBytecodeProvider();

    /**
     * Register snippet templates.
     */
    void registerSnippetTemplateCache(SnippetTemplateCache snippetTemplates);

    /**
     * Get snippet templates that were registered with
     * {@link Replacements#registerSnippetTemplateCache(SnippetTemplateCache)}.
     */
    <T extends SnippetTemplateCache> T getSnippetTemplateCache(Class<T> templatesClass);

    /**
     * Notifies this method that no further snippets will be registered via {@link #registerSnippet}
     * or {@link #registerSnippetTemplateCache}.
     *
     * This is a hook for an implementation to check for or forbid late registration.
     */
    default void closeSnippetRegistration() {
    }
}
