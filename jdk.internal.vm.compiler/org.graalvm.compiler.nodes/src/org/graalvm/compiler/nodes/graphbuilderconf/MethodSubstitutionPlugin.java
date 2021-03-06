/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.graphbuilderconf;

import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;
import static org.graalvm.compiler.core.common.GraalOptions.UseEncodedGraphs;
import static org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.INLINE_AFTER_PARSING;
import static org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.resolveType;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * An {@link InvocationPlugin} for a method where the implementation of the method is provided by a
 * {@linkplain #getSubstitute(MetaAccessProvider) substitute} method. A substitute method must be
 * static even if the substituted method is not.
 *
 * While performing intrinsification with method substitutions is simpler than writing an
 * {@link InvocationPlugin} that does manual graph weaving, it has a higher compile time cost than
 * the latter; parsing bytecodes to create nodes is slower than simply creating nodes. As such, the
 * recommended practice is to use {@link MethodSubstitutionPlugin} only for complex
 * intrinsifications which is typically those using non-straight-line control flow.
 */
public final class MethodSubstitutionPlugin implements InvocationPlugin {

    private ResolvedJavaMethod cachedSubstitute;

    /**
     * The class in which the substitute method is declared.
     */
    private final Class<?> declaringClass;

    /**
     * The name of the original and substitute method.
     */
    private final String name;

    /**
     * The parameter types of the substitute method.
     */
    private final Type[] parameters;

    private final boolean originalIsStatic;

    private final BytecodeProvider bytecodeProvider;

    /**
     * Creates a method substitution plugin.
     *
     * @param bytecodeProvider used to get the bytecodes to parse for the substitute method
     * @param declaringClass the class in which the substitute method is declared
     * @param name the name of the substitute method
     * @param parameters the parameter types of the substitute method. If the original method is not
     *            static, then {@code parameters[0]} must be the {@link Class} value denoting
     *            {@link InvocationPlugin.Receiver}
     */
    public MethodSubstitutionPlugin(BytecodeProvider bytecodeProvider, Class<?> declaringClass, String name, Type... parameters) {
        this.bytecodeProvider = bytecodeProvider;
        this.declaringClass = declaringClass;
        this.name = name;
        this.parameters = parameters;
        this.originalIsStatic = parameters.length == 0 || parameters[0] != InvocationPlugin.Receiver.class;
    }

    @Override
    public boolean inlineOnly() {
        // Conservatively assume MacroNodes may be used in a substitution
        return true;
    }

    /**
     * Gets the substitute method, resolving it first if necessary.
     */
    public ResolvedJavaMethod getSubstitute(MetaAccessProvider metaAccess) {
        if (cachedSubstitute == null) {
            cachedSubstitute = metaAccess.lookupJavaMethod(getJavaSubstitute());
        }
        return cachedSubstitute;
    }

    /**
     * Gets the object used to access the bytecodes of the substitute method.
     */
    public BytecodeProvider getBytecodeProvider() {
        return bytecodeProvider;
    }

    /**
     * Gets the class in which the substitute method is declared.
     */
    public Class<?> getDeclaringClass() {
        return declaringClass;
    }

    /**
     * Gets the reflection API version of the substitution method.
     */
    public Method getJavaSubstitute() throws GraalError {
        Method substituteMethod = lookupSubstitute();
        int modifiers = substituteMethod.getModifiers();
        if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)) {
            throw new GraalError("Substitution method must not be abstract or native: " + substituteMethod);
        }
        if (!Modifier.isStatic(modifiers)) {
            throw new GraalError("Substitution method must be static: " + substituteMethod);
        }
        return substituteMethod;
    }

    /**
     * Determines if a given method is the substitute method of this plugin.
     */
    private boolean isSubstitute(Method m) {
        if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(name)) {
            if (parameters.length == m.getParameterCount()) {
                Class<?>[] mparams = m.getParameterTypes();
                int start = 0;
                if (!originalIsStatic) {
                    start = 1;
                    if (!mparams[0].isAssignableFrom(resolveType(parameters[0], false))) {
                        return false;
                    }
                }
                for (int i = start; i < mparams.length; i++) {
                    if (mparams[i] != resolveType(parameters[i], false)) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private Method lookupSubstitute(Method excluding) {
        for (Method m : declaringClass.getDeclaredMethods()) {
            if (!m.equals(excluding) && isSubstitute(m)) {
                return m;
            }
        }
        return null;
    }

    /**
     * Gets the substitute method of this plugin.
     */
    private Method lookupSubstitute() {
        Method m = lookupSubstitute(null);
        if (m != null) {
            assert lookupSubstitute(m) == null : String.format("multiple matches found for %s:%n%s%n%s", this, m, lookupSubstitute(m));
            return m;
        }
        throw new GraalError("No method found specified by %s", this);
    }

    @Override
    public boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode[] argsIncludingReceiver) {
        if (IS_IN_NATIVE_IMAGE || (UseEncodedGraphs.getValue(b.getOptions()) && !b.parsingIntrinsic())) {
            if (!IS_IN_NATIVE_IMAGE && UseEncodedGraphs.getValue(b.getOptions())) {
                b.getReplacements().registerMethodSubstitution(this, targetMethod, INLINE_AFTER_PARSING, b.getOptions());
            }
            StructuredGraph subst = b.getReplacements().getMethodSubstitution(this,
                            targetMethod,
                            INLINE_AFTER_PARSING,
                            StructuredGraph.AllowAssumptions.ifNonNull(b.getAssumptions()),
                            null /* cancellable */,
                            b.getOptions());
            if (subst == null) {
                throw new GraalError("No graphs found for substitution %s", this);
            }
            return b.intrinsify(targetMethod, subst, receiver, argsIncludingReceiver);
        }
        ResolvedJavaMethod substitute = getSubstitute(b.getMetaAccess());
        return b.intrinsify(bytecodeProvider, targetMethod, substitute, receiver, argsIncludingReceiver);
    }

    @Override
    public StackTraceElement getApplySourceLocation(MetaAccessProvider metaAccess) {
        Class<?> c = getClass();
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals("execute")) {
                return metaAccess.lookupJavaMethod(m).asStackTraceElement(0);
            }
        }
        throw new GraalError("could not find method named \"execute\" in " + c.getName());
    }

    @Override
    public String toString() {
        return String.format("%s[%s.%s(%s)]", getClass().getSimpleName(), declaringClass.getName(), name,
                        Arrays.asList(parameters).stream().map(c -> c.getTypeName()).collect(Collectors.joining(", ")));
    }
}
