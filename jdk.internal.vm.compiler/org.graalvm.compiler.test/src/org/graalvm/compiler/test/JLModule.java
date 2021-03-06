/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.test;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.Set;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

/**
 * Facade for the {@code java.lang.Module} class introduced in JDK9 that allows tests to be
 * developed against JDK8 but use module logic if deployed on JDK9.
 */
public class JLModule {

    static {
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
            throw new AssertionError("Use of " + JLModule.class + " only allowed if " + GraalTest.class.getName() + ".JDK8OrEarlier is false");
        }
    }

    private final Object realModule;

    public JLModule(Object module) {
        this.realModule = module;
    }

    private static final Class<?> moduleClass;
    private static final Method getModuleMethod;
    private static final Method getUnnamedModuleMethod;
    private static final Method getPackagesMethod;
    private static final Method isExportedMethod;
    private static final Method isExported2Method;
    private static final Method addExportsMethod;
    /**
     * {@code jdk.internal.module.Modules.addExports(Module, String, Module)}.
     */
    private static final Method modulesAddExportsMethod;

    /**
     * {@code jdk.internal.module.Modules.addOpens(Module, String, Module)}.
     */
    private static final Method modulesAddOpensMethod;

    static {
        try {
            moduleClass = Class.forName("java.lang.Module");
            Class<?> modulesClass = Class.forName("jdk.internal.module.Modules");
            getModuleMethod = Class.class.getMethod("getModule");
            getUnnamedModuleMethod = ClassLoader.class.getMethod("getUnnamedModule");
            getPackagesMethod = moduleClass.getMethod("getPackages");
            isExportedMethod = moduleClass.getMethod("isExported", String.class);
            isExported2Method = moduleClass.getMethod("isExported", String.class, moduleClass);
            addExportsMethod = moduleClass.getMethod("addExports", String.class, moduleClass);
            modulesAddExportsMethod = modulesClass.getDeclaredMethod("addExports", moduleClass, String.class, moduleClass);
            modulesAddOpensMethod = modulesClass.getDeclaredMethod("addOpens", moduleClass, String.class, moduleClass);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static JLModule fromClass(Class<?> cls) {
        try {
            return new JLModule(getModuleMethod.invoke(cls));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static JLModule getUnnamedModuleFor(ClassLoader cl) {
        try {
            return new JLModule(getUnnamedModuleMethod.invoke(cl));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Exports all packages in this module to a given module.
     */
    public void exportAllPackagesTo(JLModule module) {
        if (this != module) {
            for (String pkg : getPackages()) {
                // Export all JVMCI packages dynamically instead
                // of requiring a long list of -XaddExports
                // options on the JVM command line.
                if (!isExported(pkg, module)) {
                    addExports(pkg, module);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public Set<String> getPackages() {
        try {
            return (Set<String>) getPackagesMethod.invoke(realModule);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public boolean isExported(String pn) {
        try {
            return (Boolean) isExportedMethod.invoke(realModule, pn);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public boolean isExported(String pn, JLModule other) {
        try {
            return (Boolean) isExported2Method.invoke(realModule, pn, other.realModule);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public void addExports(String pn, JLModule other) {
        try {
            addExportsMethod.invoke(realModule, pn, other.realModule);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Object unbox(Object obj) {
        if (obj instanceof JLModule) {
            return ((JLModule) obj).realModule;
        }
        return obj;
    }

    /**
     * Updates module m1 to export a package to module m2. Same as m1.addExports(pn, m2) but without
     * a caller check
     */
    public static void uncheckedAddExports(Object m1, String pn, Object m2) {
        try {
            modulesAddExportsMethod.invoke(null, unbox(m1), pn, unbox(m2));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Opens all packages in {@code moduleMember}'s module for deep reflection (i.e., allow
     * {@link AccessibleObject#setAccessible(boolean)} to be called for any class/method/field) by
     * {@code requestor}'s module.
     */
    public static void openAllPackagesForReflectionTo(Class<?> moduleMember, Class<?> requestor) {
        try {
            Object moduleToOpen = getModuleMethod.invoke(moduleMember);
            Object requestorModule = getModuleMethod.invoke(requestor);
            if (moduleToOpen != requestorModule) {
                String[] packages = (String[]) getPackagesMethod.invoke(moduleToOpen);
                for (String pkg : packages) {
                    modulesAddOpensMethod.invoke(moduleToOpen, pkg, requestorModule);
                }
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Opens {@code declaringClass}'s package to allow a method declared in {@code accessor} to call
     * {@link AccessibleObject#setAccessible(boolean)} on an {@link AccessibleObject} representing a
     * field or method declared by {@code declaringClass}.
     */
    public static void openForReflectionTo(Class<?> declaringClass, Class<?> accessor) {
        try {
            Object moduleToOpen = getModuleMethod.invoke(declaringClass);
            Object accessorModule = getModuleMethod.invoke(accessor);
            if (moduleToOpen != accessorModule) {
                modulesAddOpensMethod.invoke(null, moduleToOpen, declaringClass.getPackage().getName(), accessorModule);
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Exports the package named {@code packageName} declared in {@code moduleMember}'s module to
     * {@code requestor}'s module.
     */
    public static void exportPackageTo(Class<?> moduleMember, String packageName, Class<?> requestor) {
        try {
            Object moduleToExport = getModuleMethod.invoke(moduleMember);
            Object requestorModule = getModuleMethod.invoke(requestor);
            if (moduleToExport != requestorModule) {
                modulesAddExportsMethod.invoke(null, moduleToExport, packageName, requestorModule);
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
