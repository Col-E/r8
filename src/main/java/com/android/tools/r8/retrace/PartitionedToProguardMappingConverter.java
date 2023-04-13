// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.LineReader;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.retrace.internal.MappingPartitionMetadataInternal;
import com.android.tools.r8.retrace.internal.ProguardMapReaderWithFiltering.ProguardMapReaderWithFilteringInputBuffer;
import com.android.tools.r8.retrace.internal.RetracePartitionException;
import com.android.tools.r8.utils.ChainableStringConsumer;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class PartitionedToProguardMappingConverter {

  private final StringConsumer consumer;
  private final MappingPartitionFromKeySupplier partitionSupplier;
  private final byte[] metadata;
  private final DiagnosticsHandler diagnosticsHandler;

  private PartitionedToProguardMappingConverter(
      StringConsumer consumer,
      MappingPartitionFromKeySupplier partitionSupplier,
      byte[] metadata,
      DiagnosticsHandler diagnosticsHandler) {
    this.consumer = consumer;
    this.partitionSupplier = partitionSupplier;
    this.metadata = metadata;
    this.diagnosticsHandler = diagnosticsHandler;
  }

  public void run() throws RetracePartitionException {
    MappingPartitionMetadataInternal metadataInternal =
        MappingPartitionMetadataInternal.deserialize(
            metadata, MapVersion.MAP_VERSION_UNKNOWN, diagnosticsHandler);
    if (!metadataInternal.canGetPartitionKeys()) {
      throw new RetracePartitionException("Cannot obtain all partition keys from metadata");
    }
    // TODO(b/274893426): Account for preamble.
    ClassNameMapper classNameMapper = ClassNameMapper.builder().build();
    for (String partitionKey : metadataInternal.getPartitionKeys()) {
      LineReader reader =
          new ProguardMapReaderWithFilteringInputBuffer(
              new ByteArrayInputStream(partitionSupplier.get(partitionKey)), alwaysTrue(), true);
      try {
        classNameMapper =
            ClassNameMapper.mapperFromLineReaderWithFiltering(
                    reader, metadataInternal.getMapVersion(), diagnosticsHandler, true, true)
                .combine(classNameMapper);
      } catch (IOException e) {
        throw new RetracePartitionException(e);
      }
    }
    classNameMapper.sorted().write(new ProguardMapWriter(consumer, diagnosticsHandler));
  }

  private static class ProguardMapWriter implements ChainableStringConsumer {

    private final StringConsumer consumer;
    private final DiagnosticsHandler diagnosticsHandler;

    private ProguardMapWriter(StringConsumer consumer, DiagnosticsHandler diagnosticsHandler) {
      this.consumer = consumer;
      this.diagnosticsHandler = diagnosticsHandler;
    }

    @Override
    public ProguardMapWriter accept(String string) {
      consumer.accept(string, diagnosticsHandler);
      return this;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private StringConsumer consumer;
    private MappingPartitionFromKeySupplier partitionSupplier;
    private byte[] metadata;
    private DiagnosticsHandler diagnosticsHandler;

    public Builder setConsumer(StringConsumer consumer) {
      this.consumer = consumer;
      return this;
    }

    public Builder setPartitionSupplier(MappingPartitionFromKeySupplier partitionSupplier) {
      this.partitionSupplier = partitionSupplier;
      return this;
    }

    public Builder setMetadata(byte[] metadata) {
      this.metadata = metadata;
      return this;
    }

    public Builder setDiagnosticsHandler(DiagnosticsHandler diagnosticsHandler) {
      this.diagnosticsHandler = diagnosticsHandler;
      return this;
    }

    public PartitionedToProguardMappingConverter build() {
      return new PartitionedToProguardMappingConverter(
          consumer, partitionSupplier, metadata, diagnosticsHandler);
    }
  }
}
