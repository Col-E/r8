// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import com.android.tools.r8.ByteBufferProvider;
import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.debuginfo.DebugRepresentation;
import com.android.tools.r8.dex.FileWriter.ByteBufferResult;
import com.android.tools.r8.dex.FileWriter.DexContainerSection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.Timing.TimingMerger;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

class ApplicationWriterExperimental extends ApplicationWriter {
  protected ApplicationWriterExperimental(
      AppView<?> appView, Marker marker, DexIndexedConsumer consumer) {
    super(appView, marker, consumer);
  }

  @Override
  protected Collection<Timing> rewriteJumboStringsAndComputeDebugRepresentation(
      ExecutorService executorService,
      List<VirtualFile> virtualFiles,
      List<LazyDexString> lazyDexStrings)
      throws ExecutionException {
    // Collect strings from all virtual files into the last DEX section.
    VirtualFile lastFile = virtualFiles.get(virtualFiles.size() - 1);
    List<VirtualFile> allExceptLastFile = virtualFiles.subList(0, virtualFiles.size() - 1);
    for (VirtualFile virtualFile : allExceptLastFile) {
      lastFile.indexedItems.addStrings(virtualFile.indexedItems.getStrings());
    }
    Collection<Timing> timings = new ArrayList<>(virtualFiles.size());
    // Compute string layout and handle jumbo strings for the last DEX section.
    timings.add(rewriteJumboStringsAndComputeDebugRepresentation(lastFile, lazyDexStrings));
    // Handle jumbo strings for the remaining DEX sections using the string ids in the last DEX
    // section.
    timings.addAll(
        ThreadUtils.processItemsWithResults(
            allExceptLastFile,
            virtualFile ->
                rewriteJumboStringsAndComputeDebugRepresentationWithExternalStringIds(
                    virtualFile, lazyDexStrings, lastFile.getObjectMapping()),
            executorService));
    return timings;
  }

  private Timing rewriteJumboStringsAndComputeDebugRepresentationWithExternalStringIds(
      VirtualFile virtualFile, List<LazyDexString> lazyDexStrings, ObjectToOffsetMapping mapping) {
    Timing fileTiming = Timing.create("VirtualFile " + virtualFile.getId(), options);
    computeOffsetMappingAndRewriteJumboStringsWithExternalStringIds(
        virtualFile, lazyDexStrings, fileTiming, mapping);
    DebugRepresentation.computeForFile(appView, virtualFile);
    fileTiming.end();
    return fileTiming;
  }

  private void computeOffsetMappingAndRewriteJumboStringsWithExternalStringIds(
      VirtualFile virtualFile,
      List<LazyDexString> lazyDexStrings,
      Timing timing,
      ObjectToOffsetMapping mapping) {
    if (virtualFile.isEmpty()) {
      return;
    }
    timing.begin("Compute object offset mapping");
    virtualFile.computeMapping(appView, lazyDexStrings.size(), timing, mapping);
    timing.end();
    timing.begin("Rewrite jumbo strings");
    rewriteCodeWithJumboStrings(
        virtualFile.getObjectMapping(), virtualFile.classes(), appView.appInfo().app());
    timing.end();
  }

  @Override
  protected void writeVirtualFiles(
      ExecutorService executorService,
      List<VirtualFile> virtualFiles,
      List<DexString> forcedStrings,
      Timing timing)
      throws ExecutionException {
    TimingMerger merger =
        timing.beginMerger("Write files", ThreadUtils.getNumberOfThreads(executorService));
    Collection<Timing> timings;
    // TODO(b/249922554): Current limitations for the experimental flag.
    assert globalsSyntheticsConsumer == null;
    assert programConsumer == null;
    virtualFiles.forEach(
        virtualFile -> {
          assert virtualFile.getPrimaryClassDescriptor() == null;
          assert virtualFile.getFeatureSplit() == null;
        });

    ProgramConsumer consumer = options.getDexIndexedConsumer();
    ByteBufferProvider byteBufferProvider = options.getDexIndexedConsumer();
    DexOutputBuffer dexOutputBuffer = new DexOutputBuffer(byteBufferProvider);
    byte[] tempForAssertions = new byte[] {};

    int offset = 0;
    timings = new ArrayList<>();
    List<DexContainerSection> sections = new ArrayList<>();

    // TODO(b/249922554): Write in parallel.
    for (int i = 0; i < virtualFiles.size(); i++) {
      VirtualFile virtualFile = virtualFiles.get(i);
      Timing fileTiming = Timing.create("VirtualFile " + virtualFile.getId(), options);
      assert forcedStrings.size() == 0;
      DexContainerSection section =
          writeVirtualFileSection(
              virtualFile,
              fileTiming,
              forcedStrings,
              offset,
              dexOutputBuffer,
              i == virtualFiles.size() - 1);

      if (InternalOptions.assertionsEnabled()) {
        // Check that writing did not modify already written sections.
        byte[] outputSoFar = dexOutputBuffer.asArray();
        for (int j = 0; j < offset; j++) {
          assert tempForAssertions[j] == outputSoFar[j];
        }
        // Copy written sections including the one just written
        tempForAssertions = new byte[section.getLayout().getEndOfFile()];
        for (int j = 0; j < section.getLayout().getEndOfFile(); j++) {
          tempForAssertions[j] = outputSoFar[j];
        }
      }

      offset = section.getLayout().getEndOfFile();
      sections.add(section);
      fileTiming.end();
      timings.add(fileTiming);
    }
    updateStringIdsSizeAndOffset(dexOutputBuffer, sections);

    merger.add(timings);
    merger.end();

    ByteBufferResult result =
        new ByteBufferResult(
            dexOutputBuffer.stealByteBuffer(),
            sections.get(sections.size() - 1).getLayout().getEndOfFile());
    ByteDataView data =
        new ByteDataView(result.buffer.array(), result.buffer.arrayOffset(), result.length);
    // TODO(b/249922554): Add timing of passing to consumer.
    if (consumer instanceof DexFilePerClassFileConsumer) {
      assert false;
    } else {
      ((DexIndexedConsumer) consumer).accept(0, data, Sets.newIdentityHashSet(), options.reporter);
    }
  }

  private void updateStringIdsSizeAndOffset(
      DexOutputBuffer dexOutputBuffer, List<DexContainerSection> sections) {
    // The last section has the shared string_ids table. Now it is written the final size and
    // offset is known and the remaining sections can be updated to point to the shared table.
    // This updates both the size and offset in the header and in the map.
    DexContainerSection lastSection = ListUtils.last(sections);
    int stringIdsSize = lastSection.getFileWriter().getMixedSectionOffsets().getStringData().size();
    int stringIdsOffset = lastSection.getLayout().stringIdsOffset;
    for (DexContainerSection section : sections) {
      if (section != lastSection) {
        dexOutputBuffer.moveTo(section.getLayout().headerOffset + Constants.STRING_IDS_SIZE_OFFSET);
        dexOutputBuffer.putInt(stringIdsSize);
        dexOutputBuffer.putInt(stringIdsOffset);
        dexOutputBuffer.moveTo(section.getLayout().getMapOffset());
        // Skip size.
        dexOutputBuffer.getInt();
        while (dexOutputBuffer.position() < section.getLayout().getEndOfFile()) {
          int sectionType = dexOutputBuffer.getShort();
          dexOutputBuffer.getShort(); // Skip unused.
          if (sectionType == Constants.TYPE_STRING_ID_ITEM) {
            dexOutputBuffer.putInt(stringIdsSize);
            dexOutputBuffer.putInt(stringIdsOffset);
            break;
          } else {
            // Skip size and offset for this type.
            dexOutputBuffer.getInt();
            dexOutputBuffer.getInt();
          }
        }
      }
    }
  }

  private DexContainerSection writeVirtualFileSection(
      VirtualFile virtualFile,
      Timing timing,
      List<DexString> forcedStrings,
      int offset,
      DexOutputBuffer outputBuffer,
      boolean last) {
    if (virtualFile.isEmpty()) {
      return null;
    }
    printItemUseInfo(virtualFile);

    timing.begin("Reindex for lazy strings");
    ObjectToOffsetMapping objectMapping = virtualFile.getObjectMapping();
    objectMapping.computeAndReindexForLazyDexStrings(forcedStrings);
    timing.end();

    timing.begin("Write bytes");
    DexContainerSection section =
        writeDexFile(objectMapping, outputBuffer, virtualFile, timing, offset, last);
    timing.end();
    return section;
  }

  protected DexContainerSection writeDexFile(
      ObjectToOffsetMapping objectMapping,
      DexOutputBuffer dexOutputBuffer,
      VirtualFile virtualFile,
      Timing timing,
      int offset,
      boolean includeStringData) {
    FileWriter fileWriter =
        new FileWriter(
            appView,
            dexOutputBuffer,
            objectMapping,
            getDesugaredLibraryCodeToKeep(),
            virtualFile,
            includeStringData);
    // Collect the non-fixed sections.
    timing.time("collect", fileWriter::collect);
    // Generate and write the bytes.
    return timing.time("generate", () -> fileWriter.generate(offset));
  }
}
