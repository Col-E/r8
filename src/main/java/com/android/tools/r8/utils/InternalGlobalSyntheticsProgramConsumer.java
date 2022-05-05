// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.FileUtils.GLOBAL_SYNTHETIC_EXTENSION;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.GlobalSyntheticsConsumer;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.Version;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public abstract class InternalGlobalSyntheticsProgramConsumer {

  public static final String COMPILER_INFO_ENTRY_NAME = "compilerinfo";
  public static final String OUTPUT_KIND_ENTRY_NAME = "kind";

  private final GlobalSyntheticsConsumer consumer;
  private final List<Pair<String, byte[]>> content = new ArrayList<>();

  public InternalGlobalSyntheticsProgramConsumer(GlobalSyntheticsConsumer consumer) {
    this.consumer = consumer;
  }

  public abstract Kind getKind();

  synchronized void addGlobalSynthetic(String descriptor, byte[] data) {
    add(getGlobalSyntheticFileName(descriptor), data);
  }

  private void add(String entryName, byte[] data) {
    content.add(new Pair<>(entryName, data));
  }

  public void finished(DiagnosticsHandler handler) {
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
    } catch (IOException e) {
      handler.error(new ExceptionDiagnostic(e));
    }
    byte[] bytes = baos.toByteArray();
    consumer.accept(bytes);
  }

  private static String getGlobalSyntheticFileName(String descriptor) {
    assert descriptor != null && DescriptorUtils.isClassDescriptor(descriptor);
    return DescriptorUtils.getClassBinaryNameFromDescriptor(descriptor)
        + GLOBAL_SYNTHETIC_EXTENSION;
  }

  public static class InternalGlobalSyntheticsDexConsumer
      extends InternalGlobalSyntheticsProgramConsumer implements DexFilePerClassFileConsumer {

    public InternalGlobalSyntheticsDexConsumer(GlobalSyntheticsConsumer consumer) {
      super(consumer);
    }

    @Override
    public Kind getKind() {
      return Kind.DEX;
    }

    @Override
    public void accept(
        String primaryClassDescriptor,
        ByteDataView data,
        Set<String> descriptors,
        DiagnosticsHandler handler) {
      addGlobalSynthetic(primaryClassDescriptor, data.copyByteData());
    }

    @Override
    public boolean combineSyntheticClassesWithPrimaryClass() {
      return false;
    }
  }

  public static class InternalGlobalSyntheticsCfConsumer
      extends InternalGlobalSyntheticsProgramConsumer implements ClassFileConsumer {

    public InternalGlobalSyntheticsCfConsumer(GlobalSyntheticsConsumer consumer) {
      super(consumer);
    }

    @Override
    public Kind getKind() {
      return Kind.CF;
    }

    @Override
    public void accept(ByteDataView data, String descriptor, DiagnosticsHandler handler) {
      addGlobalSynthetic(descriptor, data.copyByteData());
    }
  }
}
