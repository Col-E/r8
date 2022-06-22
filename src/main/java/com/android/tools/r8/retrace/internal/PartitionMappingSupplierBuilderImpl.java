// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.retrace.MappingPartitionFromKeySupplier;
import com.android.tools.r8.retrace.PartitionMappingSupplier;
import com.android.tools.r8.retrace.PrepareMappingPartitionsCallback;
import com.android.tools.r8.retrace.RegisterMappingPartitionCallback;

public class PartitionMappingSupplierBuilderImpl extends PartitionMappingSupplier.Builder {

  private MappingPartitionFromKeySupplier partitionSupplier;
  private RegisterMappingPartitionCallback registerPartitionCallback = key -> {};
  private PrepareMappingPartitionsCallback prepare = () -> {};
  private byte[] metadata;
  private final MapVersion fallbackMapVersion;
  private boolean allowExperimental = false;

  public PartitionMappingSupplierBuilderImpl(MapVersion fallbackMapVersion) {
    this.fallbackMapVersion = fallbackMapVersion;
  }

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
  public PartitionMappingSupplier.Builder setRegisterMappingPartitionCallback(
      RegisterMappingPartitionCallback registerPartitionCallback) {
    this.registerPartitionCallback = registerPartitionCallback;
    return self();
  }

  @Override
  public PartitionMappingSupplier.Builder setPrepareMappingPartitionsCallback(
      PrepareMappingPartitionsCallback prepare) {
    this.prepare = prepare;
    return self();
  }

  @Override
  public PartitionMappingSupplier.Builder setMappingPartitionFromKeySupplier(
      MappingPartitionFromKeySupplier partitionSupplier) {
    this.partitionSupplier = partitionSupplier;
    return self();
  }

  @Override
  public PartitionMappingSupplier build() {
    if (partitionSupplier == null) {
      throw new RuntimeException(
          "Cannot build without providing a mapping partition from key supplier.");
    }
    return new PartitionMappingSupplierImpl(
        metadata,
        registerPartitionCallback,
        prepare,
        partitionSupplier,
        allowExperimental,
        fallbackMapVersion);
  }
}
