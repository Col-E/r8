// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ProguardMapConsumer;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.retrace.MappingPartition;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.retrace.ProguardMapPartitioner;
import com.android.tools.r8.retrace.internal.ProguardMapProducerInternal;
import java.io.IOException;
import java.util.function.Consumer;

public class ProguardMapPartitionConsumer extends ProguardMapConsumer {

  private final Consumer<MappingPartition> mappingPartitionConsumer;
  private final Consumer<MappingPartitionMetadata> metadataConsumer;
  private final Runnable finishedConsumer;
  private final DiagnosticsHandler diagnosticsHandler;

  private ProguardMapPartitionConsumer(
      Consumer<MappingPartition> mappingPartitionConsumer,
      Consumer<MappingPartitionMetadata> metadataConsumer,
      Runnable finishedConsumer,
      DiagnosticsHandler diagnosticsHandler) {
    this.mappingPartitionConsumer = mappingPartitionConsumer;
    this.metadataConsumer = metadataConsumer;
    this.finishedConsumer = finishedConsumer;
    this.diagnosticsHandler = diagnosticsHandler;
  }

  @Override
  public void accept(ProguardMapMarkerInfo makerInfo, ClassNameMapper classNameMapper) {
    try {
      // TODO(b/274735214): Ensure we get markerInfo consumed as well.
      MappingPartitionMetadata run =
          ProguardMapPartitioner.builder(diagnosticsHandler)
              .setProguardMapProducer(new ProguardMapProducerInternal(classNameMapper))
              .setPartitionConsumer(mappingPartitionConsumer)
              // Setting these do not actually do anything currently since there is no parsing.
              .setAllowEmptyMappedRanges(false)
              .setAllowExperimentalMapping(false)
              .build()
              .run();
      metadataConsumer.accept(run);
    } catch (IOException exception) {
      throw new Unreachable("IOExceptions should only occur when parsing");
    }
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    finishedConsumer.run();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private Consumer<MappingPartition> mappingPartitionConsumer;
    private Consumer<MappingPartitionMetadata> metadataConsumer;
    private Runnable finishedConsumer;
    private DiagnosticsHandler diagnosticsHandler;

    public Builder setMappingPartitionConsumer(
        Consumer<MappingPartition> mappingPartitionConsumer) {
      this.mappingPartitionConsumer = mappingPartitionConsumer;
      return this;
    }

    public Builder setMetadataConsumer(Consumer<MappingPartitionMetadata> metadataConsumer) {
      this.metadataConsumer = metadataConsumer;
      return this;
    }

    public Builder setFinishedConsumer(Runnable finishedConsumer) {
      this.finishedConsumer = finishedConsumer;
      return this;
    }

    public Builder setDiagnosticsHandler(DiagnosticsHandler diagnosticsHandler) {
      this.diagnosticsHandler = diagnosticsHandler;
      return this;
    }

    public ProguardMapPartitionConsumer build() {
      return new ProguardMapPartitionConsumer(
          mappingPartitionConsumer, metadataConsumer, finishedConsumer, diagnosticsHandler);
    }
  }
}
