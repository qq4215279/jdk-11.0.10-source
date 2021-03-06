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


package org.graalvm.compiler.hotspot;

import static org.graalvm.compiler.hotspot.HotSpotGraalOptionValues.GRAAL_OPTION_PROPERTY_PREFIX;
import static org.graalvm.compiler.hotspot.HotSpotGraalOptionValues.defaultOptions;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.debug.TTYStreamProvider;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.services.Services;

@ServiceProvider(TTYStreamProvider.class)
public class HotSpotTTYStreamProvider implements TTYStreamProvider {

    public static class Options {

        // @formatter:off
        @Option(help = "File to which logging is sent.  A %p in the name will be replaced with a string identifying " +
                       "the process, usually the process id and %t will be replaced by System.currentTimeMillis().", type = OptionType.Expert)
        public static final LogStreamOptionKey LogFile = new LogStreamOptionKey();
        // @formatter:on
    }

    @Override
    public PrintStream getStream() {
        return Options.LogFile.getStream();
    }

    /**
     * An option for a configurable file name that can also open a {@link PrintStream} on the file.
     * If no value is given for the option, the stream will output to HotSpot's
     * {@link HotSpotJVMCIRuntime#getLogStream() log} stream
     */
    private static class LogStreamOptionKey extends OptionKey<String> {

        LogStreamOptionKey() {
            super(null);
        }

        /**
         * @return {@code nameTemplate} with all instances of %p replaced by
         *         {@link GraalServices#getExecutionID()} and %t by
         *         {@link System#currentTimeMillis()}
         */
        private static String makeFilename(String nameTemplate) {
            String name = nameTemplate;
            if (name.contains("%p")) {
                name = name.replaceAll("%p", GraalServices.getExecutionID());
            }
            if (name.contains("%t")) {
                name = name.replaceAll("%t", String.valueOf(System.currentTimeMillis()));
            }
            return name;
        }

        /**
         * An output stream that redirects to {@link HotSpotJVMCIRuntime#getLogStream()}. The
         * {@link HotSpotJVMCIRuntime#getLogStream()} value is only accessed the first time an IO
         * operation is performed on the stream. This is required to break a deadlock in early JVMCI
         * initialization.
         */
        class DelayedOutputStream extends OutputStream {
            @NativeImageReinitialize private volatile OutputStream lazy;

            private OutputStream lazy() {
                if (lazy == null) {
                    synchronized (this) {
                        if (lazy == null) {
                            String nameTemplate = LogStreamOptionKey.this.getValue(defaultOptions());
                            if (nameTemplate != null) {
                                String name = makeFilename(nameTemplate);
                                try {
                                    final boolean enableAutoflush = true;
                                    FileOutputStream result = new FileOutputStream(name);
                                    if (!Services.IS_IN_NATIVE_IMAGE) {
                                        printVMConfig(enableAutoflush, result);
                                    } else {
                                        // There are no VM arguments for the libgraal library.
                                    }
                                    lazy = result;
                                    return lazy;
                                } catch (FileNotFoundException e) {
                                    throw new RuntimeException("couldn't open file: " + name, e);
                                }
                            }

                            lazy = HotSpotJVMCIRuntime.runtime().getLogStream();
                            PrintStream ps = new PrintStream(lazy);
                            ps.printf("[Use -D%sLogFile=<path> to redirect Graal log output to a file.]%n", GRAAL_OPTION_PROPERTY_PREFIX);
                            ps.flush();
                        }
                    }
                }
                return lazy;
            }

            @SuppressFBWarnings(value = "DLS_DEAD_LOCAL_STORE", justification = "false positive on dead store to `ps`")
            private void printVMConfig(final boolean enableAutoflush, FileOutputStream result) {
                /*
                 * Add the JVM and Java arguments to the log file to help identity it.
                 */
                PrintStream ps = new PrintStream(result, enableAutoflush);
                List<String> inputArguments = GraalServices.getInputArguments();
                if (inputArguments != null) {
                    ps.println("VM Arguments: " + String.join(" ", inputArguments));
                }
                String cmd = Services.getSavedProperties().get("sun.java.command");
                if (cmd != null) {
                    ps.println("sun.java.command=" + cmd);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                lazy().write(b, off, len);
            }

            @Override
            public void write(int b) throws IOException {
                lazy().write(b);
            }

            @Override
            public void flush() throws IOException {
                lazy().flush();
            }

            @Override
            public void close() throws IOException {
                lazy().close();
            }
        }

        /**
         * Gets the print stream configured by this option. If no file is configured, the print
         * stream will output to HotSpot's {@link HotSpotJVMCIRuntime#getLogStream() log} stream.
         */
        public PrintStream getStream() {
            return new PrintStream(new DelayedOutputStream());
        }
    }

}
