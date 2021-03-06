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


package org.graalvm.compiler.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.util.CollectionsUtil;
import org.junit.Assume;

/**
 * Utility methods for spawning a VM in a subprocess during unit tests.
 */
public final class SubprocessUtil {

    private SubprocessUtil() {
    }

    /**
     * Gets the command line for the current process.
     *
     * @return the command line arguments for the current process or {@code null} if they are not
     *         available
     */
    public static List<String> getProcessCommandLine() {
        String processArgsFile = System.getenv().get("MX_SUBPROCESS_COMMAND_FILE");
        if (processArgsFile != null) {
            try {
                return Files.readAllLines(new File(processArgsFile).toPath());
            } catch (IOException e) {
            }
        } else {
            Assume.assumeTrue("Process command line unavailable", false);
        }
        return null;
    }

    /**
     * Pattern for a single shell command argument that does not need to quoted.
     */
    private static final Pattern SAFE_SHELL_ARG = Pattern.compile("[A-Za-z0-9@%_\\-\\+=:,\\./]+");

    /**
     * Reliably quote a string as a single shell command argument.
     */
    public static String quoteShellArg(String arg) {
        if (arg.isEmpty()) {
            return "\"\"";
        }
        Matcher m = SAFE_SHELL_ARG.matcher(arg);
        if (m.matches()) {
            return arg;
        }
        // See http://stackoverflow.com/a/1250279
        return "'" + arg.replace("'", "'\"'\"'") + "'";
    }

    /**
     * Returns a new copy {@code args} with debugger arguments removed.
     */
    public static List<String> withoutDebuggerArguments(List<String> args) {
        List<String> result = new ArrayList<>(args.size());
        for (String arg : args) {
            if (!(arg.equals("-Xdebug") || arg.startsWith("-Xrunjdwp:"))) {
                result.add(arg);
            }
        }
        return result;
    }

    /**
     * Gets the command line used to start the current Java VM, including all VM arguments, but not
     * including the main class or any Java arguments. This can be used to spawn an identical VM,
     * but running different Java code.
     */
    public static List<String> getVMCommandLine() {
        List<String> args = getProcessCommandLine();
        if (args == null) {
            return null;
        } else {
            int index = findMainClassIndex(args);
            return args.subList(0, index);
        }
    }

    /**
     * Detects whether a java agent is attached.
     */
    public static boolean isJavaAgentAttached() {
        return SubprocessUtil.getVMCommandLine().stream().anyMatch(args -> args.startsWith("-javaagent"));
    }

    /**
     * The details of a subprocess execution.
     */
    public static class Subprocess {

        /**
         * The command line of the subprocess.
         */
        public final List<String> command;

        /**
         * Exit code of the subprocess.
         */
        public final int exitCode;

        /**
         * Output from the subprocess broken into lines.
         */
        public final List<String> output;

        public Subprocess(List<String> command, int exitCode, List<String> output) {
            this.command = command;
            this.exitCode = exitCode;
            this.output = output;
        }

        public static final String DASHES_DELIMITER = "-------------------------------------------------------";

        /**
         * Returns the command followed by the output as a string.
         *
         * @param delimiter if non-null, the returned string has this value as a prefix and suffix
         */
        public String toString(String delimiter) {
            Formatter msg = new Formatter();
            if (delimiter != null) {
                msg.format("%s%n", delimiter);
            }
            msg.format("%s%n", CollectionsUtil.mapAndJoin(command, e -> quoteShellArg(String.valueOf(e)), " "));
            for (String line : output) {
                msg.format("%s%n", line);
            }
            if (delimiter != null) {
                msg.format("%s%n", delimiter);
            }
            return msg.toString();
        }

        /**
         * Returns the command followed by the output as a string delimited by
         * {@value #DASHES_DELIMITER}.
         */
        @Override
        public String toString() {
            return toString(DASHES_DELIMITER);
        }
    }

    /**
     * Executes a Java subprocess.
     *
     * @param vmArgs the VM arguments
     * @param mainClassAndArgs the main class and its arguments
     */
    public static Subprocess java(List<String> vmArgs, String... mainClassAndArgs) throws IOException, InterruptedException {
        return java(vmArgs, Arrays.asList(mainClassAndArgs));
    }

    /**
     * Executes a Java subprocess.
     *
     * @param vmArgs the VM arguments
     * @param mainClassAndArgs the main class and its arguments
     */
    public static Subprocess java(List<String> vmArgs, List<String> mainClassAndArgs) throws IOException, InterruptedException {
        return javaHelper(vmArgs, null, mainClassAndArgs);
    }

    /**
     * Executes a Java subprocess.
     *
     * @param vmArgs the VM arguments
     * @param env the environment variables
     * @param mainClassAndArgs the main class and its arguments
     */
    public static Subprocess java(List<String> vmArgs, Map<String, String> env, String... mainClassAndArgs) throws IOException, InterruptedException {
        return java(vmArgs, env, Arrays.asList(mainClassAndArgs));
    }

    /**
     * Executes a Java subprocess.
     *
     * @param vmArgs the VM arguments
     * @param env the environment variables
     * @param mainClassAndArgs the main class and its arguments
     */
    public static Subprocess java(List<String> vmArgs, Map<String, String> env, List<String> mainClassAndArgs) throws IOException, InterruptedException {
        return javaHelper(vmArgs, env, mainClassAndArgs);
    }

    /**
     * Executes a Java subprocess.
     *
     * @param vmArgs the VM arguments
     * @param env the environment variables
     * @param mainClassAndArgs the main class and its arguments
     */
    private static Subprocess javaHelper(List<String> vmArgs, Map<String, String> env, List<String> mainClassAndArgs) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(vmArgs);
        command.addAll(mainClassAndArgs);
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (env != null) {
            Map<String, String> processBuilderEnv = processBuilder.environment();
            processBuilderEnv.putAll(env);
        }
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        List<String> output = new ArrayList<>();
        while ((line = stdout.readLine()) != null) {
            output.add(line);
        }
        return new Subprocess(command, process.waitFor(), output);
    }

    private static final boolean isJava8OrEarlier = JavaVersionUtil.JAVA_SPEC <= 8;

    private static boolean hasArg(String optionName) {
        if (optionName.equals("-cp") || optionName.equals("-classpath")) {
            return true;
        }
        if (!isJava8OrEarlier) {
            if (optionName.equals("--version") ||
                            optionName.equals("--show-version") ||
                            optionName.equals("--dry-run") ||
                            optionName.equals("--disable-@files") ||
                            optionName.equals("--dry-run") ||
                            optionName.equals("--help") ||
                            optionName.equals("--help-extra")) {
                return false;
            }
            if (optionName.startsWith("--")) {
                return optionName.indexOf('=') == -1;
            }
        }
        return false;
    }

    private static int findMainClassIndex(List<String> commandLine) {
        int i = 1; // Skip the java executable

        while (i < commandLine.size()) {
            String s = commandLine.get(i);
            if (s.charAt(0) != '-') {
                return i;
            } else if (hasArg(s)) {
                i += 2;
            } else {
                i++;
            }
        }
        throw new InternalError();
    }

}
