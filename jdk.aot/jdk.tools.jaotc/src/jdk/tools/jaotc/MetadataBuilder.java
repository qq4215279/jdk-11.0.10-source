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



package jdk.tools.jaotc;

import static jdk.tools.jaotc.AOTCompiledClass.getType;
import static jdk.tools.jaotc.AOTCompiledClass.metadataName;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.HotSpotGraalServices;

import jdk.tools.jaotc.binformat.BinaryContainer;
import jdk.tools.jaotc.binformat.ByteContainer;
import jdk.tools.jaotc.binformat.GotSymbol;
import jdk.tools.jaotc.utils.NativeOrderOutputStream;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.Mark;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.hotspot.HotSpotMetaData;

final class MetadataBuilder {

    private final DataBuilder dataBuilder;

    private final BinaryContainer binaryContainer;

    MetadataBuilder(DataBuilder dataBuilder) {
        this.dataBuilder = dataBuilder;
        this.binaryContainer = dataBuilder.getBinaryContainer();
    }

    /**
     * Process compiled methods and create method metadata.
     */
    void processMetadata(List<AOTCompiledClass> classes, AOTCompiledClass stubCompiledCode) {
        for (AOTCompiledClass c : classes) {
            processMetadataClass(c);
        }
        processMetadataClass(stubCompiledCode);
    }

    private void processMetadataClass(AOTCompiledClass c) {
        processInfopointsAndMarks(c);
        createMethodMetadata(c);
    }

    /**
     * Add metadata for each of the compiled methods in {@code compiledClass} to read-only section
     * of {@code binaryContainer}.
     *
     * @param compiledClass AOT Graal compilation result
     */
    private void createMethodMetadata(AOTCompiledClass compiledClass) {
        HotSpotGraalRuntimeProvider runtime = dataBuilder.getBackend().getRuntime();
        ByteContainer methodMetadataContainer = binaryContainer.getMethodMetadataContainer();

        // For each of the compiled java methods, create records holding information about them.
        for (CompiledMethodInfo methodInfo : compiledClass.getCompiledMethods()) {
            // Get the current offset in the methodmetadata container.
            final int startOffset = methodMetadataContainer.getByteStreamSize();
            assert startOffset % 8 == 0 : "Must be aligned on 8";

            methodInfo.setMetadataOffset(startOffset);

            HotSpotCompiledCode compiledMethod = methodInfo.compiledCode();
            // pc and scope description
            HotSpotMetaData metaData = new HotSpotMetaData(runtime.getTarget(), compiledMethod);

            byte[] pcDesc = metaData.pcDescBytes();
            byte[] scopeDesc = metaData.scopesDescBytes();
            byte[] relocationInfo = metaData.relocBytes();
            byte[] oopMapInfo = metaData.oopMaps();
            byte[] implicitExceptionBytes = HotSpotGraalServices.getImplicitExceptionBytes(metaData);

            // create a global symbol at this position for this method
            NativeOrderOutputStream metadataStream = new NativeOrderOutputStream();

            // get the code size
            int codeSize = methodInfo.getCodeSize();

            // get code offsets
            CodeOffsets co = CodeOffsets.buildFrom(methodInfo.getCompilationResult().getMarks());
            int unverifiedEntry = co.entry();
            int verifiedEntry = co.verifiedEntry();
            int exceptionHandler = co.exceptionHandler();
            int deoptHandler = co.deoptHandler();
            int frameSize = methodInfo.getCompilationResult().getTotalFrameSize();
            StackSlot deoptRescueSlot = methodInfo.getCompilationResult().getCustomStackArea();
            int origPcOffset = deoptRescueSlot != null ? deoptRescueSlot.getOffset(frameSize) : -1;

            // get stubs offset
            int stubsOffset = methodInfo.getStubsOffset();

            int offset = addMetadataEntries(binaryContainer, metaData, methodInfo);
            methodInfo.setMetadataGotOffset(offset);
            methodInfo.setMetadataGotSize(metaData.metadataEntries().length);
            int unsafeAccess = methodInfo.getCompilationResult().hasUnsafeAccess() ? 1 : 0;
            try {
                // calculate total size of the container
                NativeOrderOutputStream.PatchableInt totalSize = metadataStream.patchableInt();

                // @formatter:off
                metadataStream.putInt(codeSize).
                               putInt(unverifiedEntry).
                               putInt(verifiedEntry).
                               putInt(exceptionHandler).
                               putInt(deoptHandler).
                               putInt(stubsOffset).
                               putInt(frameSize).
                               putInt(origPcOffset).
                               putInt(unsafeAccess);
                // @formatter:on

                NativeOrderOutputStream.PatchableInt pcDescOffset = metadataStream.patchableInt();
                NativeOrderOutputStream.PatchableInt scopeOffset = metadataStream.patchableInt();
                NativeOrderOutputStream.PatchableInt relocationOffset = metadataStream.patchableInt();
                NativeOrderOutputStream.PatchableInt exceptionOffset = metadataStream.patchableInt();
                NativeOrderOutputStream.PatchableInt implictTableOffset = null;
                if (implicitExceptionBytes != null) {
                    implictTableOffset = metadataStream.patchableInt();
                }
                NativeOrderOutputStream.PatchableInt oopMapOffset = metadataStream.patchableInt();
                metadataStream.align(8);

                pcDescOffset.set(metadataStream.position());
                metadataStream.put(pcDesc).align(8);

                scopeOffset.set(metadataStream.position());
                metadataStream.put(scopeDesc).align(8);

                relocationOffset.set(metadataStream.position());
                metadataStream.put(relocationInfo).align(8);

                exceptionOffset.set(metadataStream.position());
                metadataStream.put(metaData.exceptionBytes()).align(8);

                if (implicitExceptionBytes != null) {
                    implictTableOffset.set(metadataStream.position());
                    metadataStream.put(implicitExceptionBytes).align(8);
                }

                // oopmaps should be last
                oopMapOffset.set(metadataStream.position());
                metadataStream.put(oopMapInfo).align(8);

                totalSize.set(metadataStream.position());

                byte[] data = metadataStream.array();

                methodMetadataContainer.appendBytes(data, 0, data.length);
            } catch (Exception e) {
                throw new InternalError("Exception occurred during compilation of " + methodInfo.getMethodInfo().getSymbolName(), e);
            }
            methodInfo.clearCompileData(); // Clear unused anymore compilation data
        }
    }

    private static int addMetadataEntries(BinaryContainer binaryContainer, HotSpotMetaData metaData, CompiledMethodInfo methodInfo) {
        Object[] metaDataEntries = metaData.metadataEntries();

        if (metaDataEntries.length == 0) {
            return 0;
        }

        int metadataGotSlotsStart = binaryContainer.getMetadataGotContainer().getByteStreamSize(); // binaryContainer.reserveMetadataGOTSlots(metaDataEntries.length);

        for (int index = 0; index < metaDataEntries.length; index++) {
            Object ref = metaDataEntries[index];
            String name = metadataName(ref);
            // Create GOT cells for klasses referenced in metadata
            addMetadataEntry(binaryContainer, name);
            // We should already have added entries for this klass
            assert AOTCompiledClass.getAOTKlassData(getType(ref)) != null;
            assert methodInfo.getDependentKlassData(getType(ref)) != null;
        }

        return metadataGotSlotsStart;
    }

    private static void addMetadataEntry(BinaryContainer binaryContainer, String name) {
        int stringOffset = binaryContainer.addMetaspaceName(name);
        binaryContainer.addMetadataGotEntry(stringOffset);
    }

    /**
     * Process {@link Infopoint}s, {@link Mark}s and {@link DataPatch}es generated by the compiler
     * to create all needed binary section constructs.
     *
     * @param compiledClass compilation result
     */
    private void processInfopointsAndMarks(AOTCompiledClass compiledClass) {
        ArrayList<CompiledMethodInfo> compiledMethods = compiledClass.getCompiledMethods();

        MarkProcessor markProcessor = new MarkProcessor(dataBuilder);
        DataPatchProcessor dataPatchProcessor = new DataPatchProcessor(dataBuilder);
        InfopointProcessor infopointProcessor = new InfopointProcessor(dataBuilder);

        for (CompiledMethodInfo methodInfo : compiledMethods) {
            CompilationResult compilationResult = methodInfo.getCompilationResult();
            String targetSymbol = "state.M" + methodInfo.getCodeId();
            String gotName = "got." + targetSymbol;
            GotSymbol symbol = binaryContainer.getMethodStateContainer().createGotSymbol(gotName);
            assert (symbol.getIndex() == methodInfo.getCodeId()) : "wrong offset";

            for (Infopoint infoPoint : compilationResult.getInfopoints()) {
                infopointProcessor.process(methodInfo, infoPoint);
            }

            for (Mark mark : compilationResult.getMarks()) {
                markProcessor.process(methodInfo, mark);
            }

            for (DataPatch dataPatch : compilationResult.getDataPatches()) {
                dataPatchProcessor.process(methodInfo, dataPatch);
            }
        }
    }

}
