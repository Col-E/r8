// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.retrace.internal.PartitionMappingSupplierBuilderImpl;

@Keep
public abstract class PartitionMappingSupplier extends MappingSupplier<PartitionMappingSupplier> {

  public static Builder builder() {
    return new PartitionMappingSupplierBuilderImpl(MapVersion.MAP_VERSION_NONE);
  }

  public static NoMetadataBuilder<?> noMetadataBuilder(MapVersion mapVersion) {
    return new PartitionMappingSupplierBuilderImpl(mapVersion);
  }

  @Keep
  public abstract static class NoMetadataBuilder<B extends NoMetadataBuilder<B>>
      extends MappingSupplierBuilder<PartitionMappingSupplier, B> {

    /***
     * Callback to be notified of a partition that is later going to be needed. When all needed
     * partitions are found the callback specified to {@code setPrepareMappingPartitionsCallback} is
     * called.
     *
     * @param registerPartitionCallback the consumer to get keys for partitions.
     */
    public abstract Builder setRegisterMappingPartitionCallback(
        RegisterMappingPartitionCallback registerPartitionCallback);

    /***
     * A callback notifying that all partitions should be prepared. The prepare callback is
     * guaranteed to be called prior to any calls to the partition supplier.
     *
     * @param prepare the callback to listen for when partitions should be prepared.
     */
    public abstract Builder setPrepareMappingPartitionsCallback(
        PrepareMappingPartitionsCallback prepare);

    /***
     * Set the partition supplier that is needed for retracing. All partitions needed has been
     * declared earlier and this should block until the bytes associated with the partition is
     * ready.
     *
     * @param partitionSupplier the function to return a partition to retrace
     */
    public abstract Builder setMappingPartitionFromKeySupplier(
        MappingPartitionFromKeySupplier partitionSupplier);
  }

  @Keep
  public abstract static class Builder extends NoMetadataBuilder<Builder> {

    /***
     * Sets the serialized metadata that was obtained when partitioning.
     *
     * @param metadata the serialized metadata
     */
    public abstract Builder setMetadata(byte[] metadata);
  }
}
