// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.PartitionMapConsumer;
import com.android.tools.r8.keepanno.annotations.KeepForApi;

@KeepForApi
public class PartitionCommand {

  private final DiagnosticsHandler diagnosticsHandler;
  private final ProguardMapProducer proguardMapProducer;
  private final PartitionMapConsumer partitionMapConsumer;

  private PartitionCommand(
      DiagnosticsHandler diagnosticsHandler,
      ProguardMapProducer proguardMapProducer,
      PartitionMapConsumer partitionMapConsumer) {
    this.diagnosticsHandler = diagnosticsHandler;
    this.proguardMapProducer = proguardMapProducer;
    this.partitionMapConsumer = partitionMapConsumer;
  }

  public DiagnosticsHandler getDiagnosticsHandler() {
    return diagnosticsHandler;
  }

  public ProguardMapProducer getProguardMapProducer() {
    return proguardMapProducer;
  }

  public PartitionMapConsumer getPartitionMapConsumer() {
    return partitionMapConsumer;
  }

  /** Utility method for obtaining a RetraceCommand builder with a default diagnostics handler. */
  public static Builder builder() {
    return new Builder(new DiagnosticsHandler() {});
  }

  @KeepForApi
  public static class Builder {

    private final DiagnosticsHandler diagnosticsHandler;
    private ProguardMapProducer proguardMapProducer;
    private PartitionMapConsumer partitionMapConsumer;

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      this.diagnosticsHandler = diagnosticsHandler;
    }

    public Builder setProguardMapProducer(ProguardMapProducer proguardMapProducer) {
      this.proguardMapProducer = proguardMapProducer;
      return this;
    }

    public Builder setPartitionMapConsumer(PartitionMapConsumer partitionMapConsumer) {
      this.partitionMapConsumer = partitionMapConsumer;
      return this;
    }

    public PartitionCommand build() {
      if (proguardMapProducer == null) {
        throw new RetracePartitionException("ProguardMapSupplier not specified");
      }
      if (partitionMapConsumer == null) {
        throw new RetracePartitionException("PartitionMapConsumer not specified");
      }
      return new PartitionCommand(diagnosticsHandler, proguardMapProducer, partitionMapConsumer);
    }
  }
}
