// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Finishable;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.LineReader;
import com.android.tools.r8.retrace.internal.MappingPartitionMetadataInternal;
import com.android.tools.r8.retrace.internal.MetadataAdditionalInfo;
import com.android.tools.r8.retrace.internal.PartitionMappingSupplierBase;
import com.android.tools.r8.retrace.internal.ProguardMapReaderWithFiltering.ProguardMapReaderWithFilteringInputBuffer;
import com.android.tools.r8.utils.ChainableStringConsumer;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class PartitionedToProguardMappingConverter {

  private final StringConsumer consumer;
  private final PartitionMappingSupplierBase<?> partitionMappingSupplier;
  private final DiagnosticsHandler diagnosticsHandler;

  private PartitionedToProguardMappingConverter(
      StringConsumer consumer,
      PartitionMappingSupplierBase<?> partitionMappingSupplier,
      DiagnosticsHandler diagnosticsHandler) {
    this.consumer = consumer;
    this.partitionMappingSupplier = partitionMappingSupplier;
    this.diagnosticsHandler = diagnosticsHandler;
  }

  private MappingPartitionMetadataInternal getMetadata() {
    MappingPartitionMetadataInternal metadataInternal =
        partitionMappingSupplier.getMetadata(diagnosticsHandler);
    if (metadataInternal == null || !metadataInternal.canGetPartitionKeys()) {
      throw new RetracePartitionException("Cannot obtain all partition keys from metadata");
    }
    return metadataInternal;
  }

  private void requestKeys(MappingPartitionMetadataInternal metadataInternal) {
    for (String partitionKey : metadataInternal.getPartitionKeys()) {
      partitionMappingSupplier.registerKeyUse(partitionKey);
    }
  }

  private void run(
      MappingPartitionMetadataInternal metadataInternal,
      MappingPartitionFromKeySupplier partitionSupplier)
      throws RetracePartitionException {
    ProguardMapWriter consumer = new ProguardMapWriter(this.consumer, diagnosticsHandler);
    if (metadataInternal.canGetAdditionalInfo()) {
      MetadataAdditionalInfo additionalInfo = metadataInternal.getAdditionalInfo();
      if (additionalInfo.hasPreamble()) {
        additionalInfo.getPreamble().forEach(line -> consumer.accept(line).accept("\n"));
      }
    }
    for (String partitionKey : metadataInternal.getPartitionKeys()) {
      LineReader reader =
          new ProguardMapReaderWithFilteringInputBuffer(
              new ByteArrayInputStream(partitionSupplier.get(partitionKey)), alwaysTrue(), true);
      try {
        ClassNameMapper.mapperFromLineReaderWithFiltering(
                reader,
                metadataInternal.getMapVersion(),
                diagnosticsHandler,
                true,
                true,
                partitionBuilder -> partitionBuilder.setBuildPreamble(true))
            .write(consumer);
      } catch (IOException e) {
        throw new RetracePartitionException(e);
      }
    }
    consumer.finished(diagnosticsHandler);
    partitionMappingSupplier.finished(diagnosticsHandler);
  }

  public void run() throws RetracePartitionException {
    MappingPartitionMetadataInternal metadata = getMetadata();
    PartitionMappingSupplier syncSupplier = partitionMappingSupplier.getPartitionMappingSupplier();
    if (syncSupplier == null) {
      throw new RetracePartitionException(
          "Running synchronously requires a synchronous partition mapping provider. Use runAsync()"
              + " if you have an asynchronous provider.");
    }
    requestKeys(metadata);
    run(metadata, syncSupplier.getMappingPartitionFromKeySupplier());
  }

  public RetraceAsyncAction runAsync() throws RetracePartitionException {
    MappingPartitionMetadataInternal metadata = getMetadata();
    requestKeys(metadata);
    return supplier -> run(metadata, supplier);
  }

  private static class ProguardMapWriter implements ChainableStringConsumer, Finishable {

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

    @Override
    public void finished(DiagnosticsHandler handler) {
      consumer.finished(handler);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private StringConsumer consumer;
    private PartitionMappingSupplierBase<?> partitionSupplier;
    private DiagnosticsHandler diagnosticsHandler;

    public Builder setConsumer(StringConsumer consumer) {
      this.consumer = consumer;
      return this;
    }

    public Builder setPartitionMappingSupplier(PartitionMappingSupplierBase<?> partitionSupplier) {
      this.partitionSupplier = partitionSupplier;
      return this;
    }

    public Builder setDiagnosticsHandler(DiagnosticsHandler diagnosticsHandler) {
      this.diagnosticsHandler = diagnosticsHandler;
      return this;
    }

    public PartitionedToProguardMappingConverter build() {
      return new PartitionedToProguardMappingConverter(
          consumer, partitionSupplier, diagnosticsHandler);
    }
  }
}
