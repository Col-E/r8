// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.PartitionMapConsumer;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.MapConsumer;
import com.android.tools.r8.naming.ProguardMapMarkerInfo;
import com.android.tools.r8.retrace.MappingPartition;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;

public class MapConsumerUtils {

  public static MapConsumer wrapExistingMapConsumer(
      MapConsumer existingMapConsumer, MapConsumer newConsumer) {
    if (existingMapConsumer == null) {
      return newConsumer;
    }
    return new MapConsumer() {
      @Override
      public void accept(
          DiagnosticsHandler diagnosticsHandler,
          ProguardMapMarkerInfo makerInfo,
          ClassNameMapper classNameMapper) {
        existingMapConsumer.accept(diagnosticsHandler, makerInfo, classNameMapper);
        newConsumer.accept(diagnosticsHandler, makerInfo, classNameMapper);
      }

      @Override
      public void finished(DiagnosticsHandler handler) {
        existingMapConsumer.finished(handler);
        newConsumer.finished(handler);
      }
    };
  }

  public static <T> MapConsumer wrapExistingMapConsumerIfNotNull(
      MapConsumer existingMapConsumer, T object, Function<T, MapConsumer> producer) {
    if (object == null) {
      return existingMapConsumer;
    }
    return wrapExistingMapConsumer(existingMapConsumer, producer.apply(object));
  }

  public static PartitionMapConsumer createZipConsumer(Path path) {
    return new PartitionMapConsumer() {

      private final Box<ZipBuilder> zipBuilderBox = new Box<>();

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
      public void acceptMappingPartitionMetadata(
          MappingPartitionMetadata mappingPartitionMetadata) {
        try {
          zipBuilderBox
              .computeIfAbsentThrowing(() -> ZipBuilder.builder(path))
              .addBytes("METADATA", mappingPartitionMetadata.getBytes());
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
    };
  }
}
