// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.PartitionMapConsumer;
import com.android.tools.r8.retrace.MappingPartition;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.retrace.PartitionMappingSupplier;
import com.android.tools.r8.retrace.RetracePartitionException;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PartitionMapZipContainer {

  private static final byte[] EMPTY_RESULT = new byte[0];
  private static final String METADATA_NAME = "METADATA";

  public static PartitionMappingSupplier createPartitionMapZipContainerSupplier(Path path)
      throws Exception {
    ZipFile zipFile = new ZipFile(path.toFile());
    byte[] metadata =
        ByteStreams.toByteArray(zipFile.getInputStream(zipFile.getEntry(METADATA_NAME)));
    return PartitionMappingSupplier.builder()
        .setMetadata(metadata)
        .setMappingPartitionFromKeySupplier(
            key -> {
              try {
                ZipEntry entry = zipFile.getEntry(key);
                return entry == null
                    ? EMPTY_RESULT
                    : ByteStreams.toByteArray(zipFile.getInputStream(entry));
              } catch (IOException e) {
                throw new RetracePartitionException(e);
              }
            })
        .setFinishedPartitionMappingCallback(
            diagnosticsHandler -> {
              try {
                zipFile.close();
              } catch (IOException e) {
                throw new RetracePartitionException(e);
              }
            })
        .build();
  }

  public static PartitionMapConsumer createPartitionMapZipContainerConsumer(Path path) {
    return new Consumer(path);
  }

  public static class Consumer implements PartitionMapConsumer {

    private final Box<ZipBuilder> zipBuilderBox = new Box<>();
    private final Path path;

    private Consumer(Path path) {
      this.path = path;
    }

    @Override
    public void acceptMappingPartition(MappingPartition mappingPartition) {
      try {
        zipBuilderBox
            .computeIfAbsentThrowing(() -> ZipBuilder.builder(path))
            .addBytes(mappingPartition.getKey(), mappingPartition.getPayload());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void acceptMappingPartitionMetadata(MappingPartitionMetadata mappingPartitionMetadata) {
      try {
        zipBuilderBox
            .computeIfAbsentThrowing(() -> ZipBuilder.builder(path))
            .addBytes(METADATA_NAME, mappingPartitionMetadata.getBytes());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      try {
        zipBuilderBox.computeIfAbsentThrowing(() -> ZipBuilder.builder(path)).build();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
