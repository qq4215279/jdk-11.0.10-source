/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.aarch64;

import static org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.registerPlatformSpecificUnsafePlugins;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.COS;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.EXP;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG10;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.SIN;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.TAN;

import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticLIRGeneratorTool.RoundingMode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.AtomicReadAndAddNode;
import org.graalvm.compiler.nodes.java.AtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.misc.Unsafe;

public class AArch64GraphBuilderPlugins {

    public static void register(Plugins plugins, BytecodeProvider bytecodeProvider, boolean explicitUnsafeNullChecks,
                    boolean registerMathPlugins) {
        register(plugins, bytecodeProvider, explicitUnsafeNullChecks, registerMathPlugins, true);
    }

    public static void register(Plugins plugins, BytecodeProvider bytecodeProvider, boolean explicitUnsafeNullChecks,
                    boolean registerMathPlugins, boolean emitJDK9StringSubstitutions) {
        InvocationPlugins invocationPlugins = plugins.getInvocationPlugins();
        invocationPlugins.defer(new Runnable() {
            @Override
            public void run() {
                registerIntegerLongPlugins(invocationPlugins, JavaKind.Int, bytecodeProvider);
                registerIntegerLongPlugins(invocationPlugins, JavaKind.Long, bytecodeProvider);
                if (registerMathPlugins) {
                    registerMathPlugins(invocationPlugins);
                }
                if (emitJDK9StringSubstitutions) {
                    registerStringLatin1Plugins(invocationPlugins, bytecodeProvider);
                    registerStringUTF16Plugins(invocationPlugins, bytecodeProvider);
                }
                registerUnsafePlugins(invocationPlugins, bytecodeProvider);
                // This is temporarily disabled until we implement correct emitting of the CAS
                // instructions of the proper width.
                registerPlatformSpecificUnsafePlugins(invocationPlugins, bytecodeProvider, explicitUnsafeNullChecks,
                                new JavaKind[]{JavaKind.Int, JavaKind.Long, JavaKind.Object});
            }
        });
    }

    private static void registerIntegerLongPlugins(InvocationPlugins plugins, JavaKind kind, BytecodeProvider bytecodeProvider) {
        Class<?> declaringClass = kind.toBoxedJavaClass();
        Class<?> type = kind.toJavaClass();
        Registration r = new Registration(plugins, declaringClass, bytecodeProvider);
        r.register1("numberOfLeadingZeros", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                ValueNode folded = AArch64CountLeadingZerosNode.tryFold(value);
                if (folded != null) {
                    b.addPush(JavaKind.Int, folded);
                } else {
                    b.addPush(JavaKind.Int, new AArch64CountLeadingZerosNode(value));
                }
                return true;
            }
        });
        r.register1("numberOfTrailingZeros", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                ValueNode folded = AArch64CountTrailingZerosNode.tryFold(value);
                if (folded != null) {
                    b.addPush(JavaKind.Int, folded);
                } else {
                    b.addPush(JavaKind.Int, new AArch64CountTrailingZerosNode(value));
                }
                return true;
            }
        });
        r.register1("bitCount", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(new AArch64BitCountNode(value).canonical(null)));
                return true;
            }
        });
    }

    private static void registerMathPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Math.class);
        registerUnaryMath(r, "sin", SIN);
        registerUnaryMath(r, "cos", COS);
        registerUnaryMath(r, "tan", TAN);
        registerUnaryMath(r, "exp", EXP);
        registerUnaryMath(r, "log", LOG);
        registerUnaryMath(r, "log10", LOG10);
        r.register2("pow", Double.TYPE, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(JavaKind.Double, b.append(BinaryMathIntrinsicNode.create(x, y, BinaryMathIntrinsicNode.BinaryOperation.POW)));
                return true;
            }
        });
        registerRound(r, "rint", RoundingMode.NEAREST);
        registerRound(r, "ceil", RoundingMode.UP);
        registerRound(r, "floor", RoundingMode.DOWN);
    }

    private static void registerUnaryMath(Registration r, String name, UnaryOperation operation) {
        r.register1(name, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(UnaryMathIntrinsicNode.create(value, operation)));
                return true;
            }
        });
    }

    private static void registerRound(Registration r, String name, RoundingMode mode) {
        r.register1(name, Double.TYPE, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                b.push(JavaKind.Double, b.append(new AArch64RoundNode(arg, mode)));
                return true;
            }
        });
    }

    private static void registerStringLatin1Plugins(InvocationPlugins plugins, BytecodeProvider replacementsBytecodeProvider) {
        if (JavaVersionUtil.JAVA_SPEC >= 9) {
            Registration r = new Registration(plugins, "java.lang.StringLatin1", replacementsBytecodeProvider);
            r.setAllowOverwrite(true);
            r.registerMethodSubstitution(AArch64StringLatin1Substitutions.class, "compareTo", byte[].class, byte[].class);
            r.registerMethodSubstitution(AArch64StringLatin1Substitutions.class, "compareToUTF16", byte[].class, byte[].class);
        }
    }

    private static void registerStringUTF16Plugins(InvocationPlugins plugins, BytecodeProvider replacementsBytecodeProvider) {
        if (JavaVersionUtil.JAVA_SPEC >= 9) {
            Registration r = new Registration(plugins, "java.lang.StringUTF16", replacementsBytecodeProvider);
            r.setAllowOverwrite(true);
            r.registerMethodSubstitution(AArch64StringUTF16Substitutions.class, "compareTo", byte[].class, byte[].class);
            r.registerMethodSubstitution(AArch64StringUTF16Substitutions.class, "compareToLatin1", byte[].class, byte[].class);
        }
    }

    private static void registerUnsafePlugins(InvocationPlugins plugins, BytecodeProvider replacementsBytecodeProvider) {
        registerUnsafePlugins(new Registration(plugins, Unsafe.class), new JavaKind[]{JavaKind.Int, JavaKind.Long, JavaKind.Object});
        if (JavaVersionUtil.JAVA_SPEC > 8) {
            registerUnsafePlugins(new Registration(plugins, "jdk.internal.misc.Unsafe", replacementsBytecodeProvider), new JavaKind[]{JavaKind.Int, JavaKind.Long, JavaKind.Object});
        }
    }

    private static void registerUnsafePlugins(Registration r, JavaKind[] unsafeJavaKinds) {

        for (JavaKind kind : unsafeJavaKinds) {
            Class<?> javaClass = kind == JavaKind.Object ? Object.class : kind.toJavaClass();

            r.register4("getAndSet" + kind.name(), Receiver.class, Object.class, long.class, javaClass, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode value) {
                    // Emits a null-check for the otherwise unused receiver
                    unsafe.get();
                    b.addPush(kind, new AtomicReadAndWriteNode(object, offset, value, kind, LocationIdentity.any()));
                    b.getGraph().markUnsafeAccess();
                    return true;
                }
            });

            if (kind != JavaKind.Boolean && kind.isNumericInteger()) {
                r.register4("getAndAdd" + kind.name(), Receiver.class, Object.class, long.class, javaClass, new InvocationPlugin() {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode object, ValueNode offset, ValueNode delta) {
                        // Emits a null-check for the otherwise unused receiver
                        unsafe.get();
                        AddressNode address = b.add(new OffsetAddressNode(object, offset));
                        b.addPush(kind, new AtomicReadAndAddNode(address, delta, kind, LocationIdentity.any()));
                        b.getGraph().markUnsafeAccess();
                        return true;
                    }
                });
            }
        }
    }
}
