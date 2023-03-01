// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.FileUtils.GLOBAL_SYNTHETIC_EXTENSION;

import com.android.tools.r8.ByteBufferProvider;
import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DexFilePerClassFileConsumerData;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.GlobalSyntheticsConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.Version;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.Reference;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public abstract class InternalGlobalSyntheticsProgramConsumer
    implements ProgramConsumer, ByteBufferProvider {

  public static final String COMPILER_INFO_ENTRY_NAME = "compilerinfo";
  public static final String OUTPUT_KIND_ENTRY_NAME = "kind";

  // Builder for constructing a valid "globals" data payload.
  private static class GlobalsFileBuilder {

    private final Kind kind;
    private final List<Pair<String, byte[]>> content = new ArrayList<>();

    public GlobalsFileBuilder(Kind kind) {
      this.kind = kind;
    }

    public Kind getKind() {
      return kind;
    }

    void addGlobalSynthetic(String descriptor, byte[] data) {
      add(getGlobalSyntheticFileName(descriptor), data);
    }

    private void add(String entryName, byte[] data) {
      content.add(new Pair<>(entryName, data));
    }

    public byte[] build() throws IOException {
      // Add meta information.
      add(COMPILER_INFO_ENTRY_NAME, Version.getVersionString().getBytes(StandardCharsets.UTF_8));
      add(OUTPUT_KIND_ENTRY_NAME, getKind().toString().getBytes(StandardCharsets.UTF_8));

      // Size estimate to avoid reallocation of the byte output array.
      final int zipHeaderOverhead = 500;
      final int zipEntryOverhead = 200;
      int estimatedZipSize =
          zipHeaderOverhead
              + ListUtils.fold(
                  content,
                  0,
                  (acc, pair) ->
                      acc + pair.getFirst().length() + pair.getSecond().length + zipEntryOverhead);
      ByteArrayOutputStream baos = new ByteArrayOutputStream(estimatedZipSize);
      try (ZipOutputStream stream = new ZipOutputStream(baos)) {
        for (Pair<String, byte[]> pair : content) {
          ZipUtils.writeToZipStream(stream, pair.getFirst(), pair.getSecond(), ZipEntry.STORED);
          // Clear out the bytes to avoid three copies when converting the boas.
          pair.setSecond(null);
        }
      }
      return baos.toByteArray();
    }

    private static String getGlobalSyntheticFileName(String descriptor) {
      assert descriptor != null && DescriptorUtils.isClassDescriptor(descriptor);
      return DescriptorUtils.getClassBinaryNameFromDescriptor(descriptor)
          + GLOBAL_SYNTHETIC_EXTENSION;
    }
  }

  public static class InternalGlobalSyntheticsDexIndexedConsumer
      extends InternalGlobalSyntheticsProgramConsumer implements DexFilePerClassFileConsumer {

    private final GlobalSyntheticsConsumer clientConsumer;
    private final GlobalsFileBuilder builder = new GlobalsFileBuilder(Kind.DEX);

    public InternalGlobalSyntheticsDexIndexedConsumer(GlobalSyntheticsConsumer clientConsumer) {
      this.clientConsumer = clientConsumer;
    }

    @Override
    public synchronized void acceptDexFile(DexFilePerClassFileConsumerData data) {
      builder.addGlobalSynthetic(data.getPrimaryClassDescriptor(), data.getByteDataCopy());
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      throw new Unreachable("Unexpected call to non-internal finished.");
    }

    @Override
    public void finished(AppView<?> appView) {
      byte[] bytes = null;
      try {
        bytes = builder.build();
      } catch (IOException e) {
        appView.reporter().error(new ExceptionDiagnostic(e));
      }
      if (bytes != null) {
        clientConsumer.accept(ByteDataView.of(bytes), null, appView.reporter());
      }
      clientConsumer.finished(appView.reporter());
    }

    @Override
    public boolean combineSyntheticClassesWithPrimaryClass() {
      return false;
    }
  }

  public static class InternalGlobalSyntheticsDexPerFileConsumer extends PerFileBase
      implements DexFilePerClassFileConsumer {

    public InternalGlobalSyntheticsDexPerFileConsumer(
        GlobalSyntheticsConsumer consumer, AppView appView) {
      super(consumer, appView);
    }

    @Override
    Kind getKind() {
      return Kind.DEX;
    }

    @Override
    public void acceptDexFile(DexFilePerClassFileConsumerData data) {
      addGlobal(data.getPrimaryClassDescriptor(), data.getByteDataView());
    }

    @Override
    public boolean combineSyntheticClassesWithPrimaryClass() {
      return false;
    }
  }

  public static class InternalGlobalSyntheticsCfConsumer extends PerFileBase
      implements ClassFileConsumer {

    public InternalGlobalSyntheticsCfConsumer(GlobalSyntheticsConsumer consumer, AppView appView) {
      super(consumer, appView);
    }

    @Override
    Kind getKind() {
      return Kind.CF;
    }

    @Override
    public void accept(ByteDataView data, String descriptor, DiagnosticsHandler handler) {
      addGlobal(descriptor, data);
    }
  }

  private abstract static class PerFileBase extends InternalGlobalSyntheticsProgramConsumer {

    private final AppView appView;
    private final GlobalSyntheticsConsumer clientConsumer;
    private final Map<DexType, byte[]> globalToBytes = new ConcurrentHashMap<>();

    public PerFileBase(GlobalSyntheticsConsumer consumer, AppView appView) {
      this.appView = appView;
      this.clientConsumer = consumer;
    }

    abstract Kind getKind();

    @Override
    public final void finished(DiagnosticsHandler handler) {
      throw new Unreachable("Unexpected call to non-internal finished.");
    }

    @Override
    public void finished(AppView<?> appView) {
      Map<DexType, Set<DexType>> globalsToContexts =
          appView.getSyntheticItems().getFinalGlobalSyntheticContexts(appView);
      Map<DexType, Set<DexType>> contextToGlobals = new IdentityHashMap<>();
      for (DexType globalType : globalToBytes.keySet()) {
        // It would be good to assert that the global is a synthetic type, but the naming-lens
        // is not applied to SyntheticItems in AppView.
        Set<DexType> contexts = globalsToContexts.get(globalType);
        // TODO(b/231598779): Contexts should never be null once fixed for records.
        assert contexts != null;
        assert !contexts.isEmpty();
        for (DexType contextType : contexts) {
          contextToGlobals
              .computeIfAbsent(contextType, k -> SetUtils.newIdentityHashSet())
              .add(globalType);
        }
      }
      contextToGlobals.forEach(
          (context, globals) -> {
            GlobalsFileBuilder builder = new GlobalsFileBuilder(getKind());
            globals.forEach(
                global ->
                    builder.addGlobalSynthetic(
                        global.toDescriptorString(), globalToBytes.get(global)));
            byte[] bytes = null;
            try {
              bytes = builder.build();
            } catch (IOException e) {
              appView.reporter().error(new ExceptionDiagnostic(e));
            }
            if (bytes != null) {
              clientConsumer.accept(
                  ByteDataView.of(bytes),
                  Reference.classFromDescriptor(context.toDescriptorString()),
                  appView.reporter());
            }
          });
      clientConsumer.finished(appView.reporter());
    }

    void addGlobal(String descriptor, ByteDataView data) {
      DexType type = appView.dexItemFactory().createType(descriptor);
      globalToBytes.put(type, data.copyByteData());
    }
  }

  public abstract void finished(AppView<?> appView);
}
