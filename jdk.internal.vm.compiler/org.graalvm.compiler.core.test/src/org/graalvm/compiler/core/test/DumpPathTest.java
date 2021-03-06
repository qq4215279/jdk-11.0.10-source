/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.internal.vm.compiler.collections.EconomicMap;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.DebugOptions.PrintGraphTarget;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Test;

/**
 * Check that setting the dump path results in files ending up in the right directory with matching
 * names.
 */
public class DumpPathTest extends GraalCompilerTest {

    public static Object snippet() {
        return new String("snippet");
    }

    @Test
    public void testDump() throws IOException {
        assumeManagementLibraryIsLoadable();
        Path dumpDirectoryPath = Files.createTempDirectory("DumpPathTest");
        String[] extensions = new String[]{".cfg", ".bgv", ".graph-strings"};
        EconomicMap<OptionKey<?>, Object> overrides = OptionValues.newOptionMap();
        overrides.put(DebugOptions.DumpPath, dumpDirectoryPath.toString());
        overrides.put(DebugOptions.PrintCFG, true);
        overrides.put(DebugOptions.PrintGraph, PrintGraphTarget.File);
        overrides.put(DebugOptions.PrintCanonicalGraphStrings, true);
        overrides.put(DebugOptions.Dump, "*");

        // Generate dump files.
        test(new OptionValues(getInitialOptions(), overrides), "snippet");
        // Check that IGV files got created, in the right place.
        checkForFiles(dumpDirectoryPath, extensions);

        // Clean up the generated files.
        removeDirectory(dumpDirectoryPath);
    }

    /**
     * Check that the given directory contains file or directory names with all the given
     * extensions.
     */
    private static void checkForFiles(Path directoryPath, String[] extensions) throws IOException {
        String[] paths = new String[extensions.length];
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directoryPath)) {
            for (Path filePath : stream) {
                String fileName = filePath.getFileName().toString();
                for (int i = 0; i < extensions.length; i++) {
                    String extension = extensions[i];
                    if (fileName.endsWith(extensions[i])) {
                        assertTrue(paths[i] == null, "multiple files found for %s in %s", extension, directoryPath);
                        paths[i] = fileName.replace(extensions[i], "");
                    }
                }
            }
        }
        for (int i = 0; i < paths.length; i++) {
            assertTrue(paths[i] != null, "missing file for extension %s in %s", extensions[i], directoryPath);
        }
        // Ensure that all file names are the same.
        for (int i = 1; i < paths.length; i++) {
            assertTrue(paths[0].equals(paths[i]), paths[0] + " != " + paths[i]);
        }
    }
}
