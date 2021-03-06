/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.replacements.StringSubstitutions;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsNode;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests {@link StringSubstitutions}.
 */
public class StringSubstitutionsTest extends MethodSubstitutionTest {

    public void testSubstitution(String testMethodName, Class<?> intrinsicClass, Class<?> holder, String methodName, boolean optional, Object[] args1, Object[] args2) {
        ResolvedJavaMethod realMethod = getResolvedJavaMethod(holder, methodName);
        ResolvedJavaMethod testMethod = getResolvedJavaMethod(testMethodName);
        StructuredGraph graph = testGraph(testMethodName);

        // Check to see if the resulting graph contains the expected node
        StructuredGraph replacement = getReplacements().getSubstitution(realMethod, -1, false, null, graph.getOptions());
        if (replacement == null && !optional) {
            assertInGraph(graph, intrinsicClass);
        }

        // Force compilation
        InstalledCode code = getCode(testMethod);
        assert optional || code != null;

        for (int i = 0; i < args1.length; i++) {
            Object arg1 = args1[i];
            Object arg2 = args2[i];
            Object expected = invokeSafe(realMethod, arg1, arg2);
            // Verify that the original method and the substitution produce the same value
            assertDeepEquals(expected, invokeSafe(testMethod, null, arg1, arg2));
            // Verify that the generated code and the original produce the same value
            assertDeepEquals(expected, executeVarargsSafe(code, arg1, arg2));
        }
    }

    @Test
    public void testEquals() {
        if (JavaVersionUtil.JAVA_SPEC > 8) {
            // StringSubstitutions are disabled in 1.9
            return;
        }

        final int n = 1000;
        Object[] args1 = new Object[n];
        Object[] args2 = new Object[n];

        // equal strings
        String s1 = "";
        String s2 = "";
        for (int i = 0; i < n / 2; i++) {
            args1[i] = s1;
            args2[i] = s2;
            s1 = s1 + "0";
            s2 = s2 + "0";
        }

        // non-equal strings
        s1 = "";
        s2 = "";
        for (int i = n / 2; i < n; i++) {
            args1[i] = s1;
            args2[i] = s2;
            s2 = s1 + "1";
            s1 = s1 + "0";
        }

        testSubstitution("stringEquals", ArrayEqualsNode.class, String.class, "equals", false, args1, args2);
    }

    @SuppressWarnings("all")
    public static boolean stringEquals(String a, String b) {
        return a.equals(b);
    }

    @Test
    public void testIndexOfConstant() {
        test("indexOfConstant");
    }

    public int indexOfConstant() {
        String foobar = "foobar";
        String bar = "bar";
        return foobar.indexOf(bar);
    }

    @Test
    public void testIndexOfConstantUTF16() {
        test("indexOfConstantUTF16case1");
        test("indexOfConstantUTF16case2");
        test("indexOfConstantUTF16case3");
    }

    public int indexOfConstantUTF16case1() {
        return ("grga " + ((char) 0x10D) + "varak").indexOf(((char) 0x10D) + "varak");
    }

    public int indexOfConstantUTF16case2() {
        int index = ("grga " + ((char) 0xD) + "varak").indexOf(((char) 0x10D) + "varak");
        return index;
    }

    public int indexOfConstantUTF16case3() {
        int index = ("grga " + ((char) 0x100) + "varak").indexOf(((char) 0x10D) + "varak");
        return index;
    }

    @Test
    public void testCompareTo() {
        test("compareTo");
    }

    public int compareTo() {
        return "ofar".compareTo("rafo");
    }
}
