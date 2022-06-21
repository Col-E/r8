// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.retrace.PartitionMappingSupplier;
import com.android.tools.r8.utils.ConsumerUtils;
import java.util.function.Consumer;
import java.util.function.Function;

public class PartitionMappingSupplierBuilderImpl extends PartitionMappingSupplier.Builder {

  private Function<String, byte[]> partitionSupplier;
  private Consumer<String> partitionToFetchConsumer = ConsumerUtils.emptyConsumer();
  private Runnable prepare = () -> {};
  private byte[] metadata;
  private boolean allowExperimental = false;

  @Override
  public PartitionMappingSupplier.Builder self() {
    return this;
  }

  @Override
  public PartitionMappingSupplier.Builder setAllowExperimental(boolean allowExperimental) {
    this.allowExperimental = allowExperimental;
    return self();
  }

  @Override
  public PartitionMappingSupplier.Builder setMetadata(byte[] metadata) {
    this.metadata = metadata;
    return self();
  }

  @Override
  public PartitionMappingSupplier.Builder setPartitionSupplier(
      Function<String, byte[]> partitionSupplier) {
    this.partitionSupplier = partitionSupplier;
    return self();
  }

  @Override
  public PartitionMappingSupplier.Builder setPartitionToFetchConsumer(
      Consumer<String> partitionToFetchConsumer) {
    this.partitionToFetchConsumer = partitionToFetchConsumer;
    return self();
  }

  @Override
  public PartitionMappingSupplier.Builder setPrepareNewPartitions(Runnable prepare) {
    this.prepare = prepare;
    return self();
  }

  @Override
  public PartitionMappingSupplier build() {
    return new PartitionMappingSupplierImpl(
        metadata, partitionToFetchConsumer, prepare, partitionSupplier, allowExperimental);
  }
}
