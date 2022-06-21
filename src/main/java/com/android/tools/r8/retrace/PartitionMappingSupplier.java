// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.retrace.internal.PartitionMappingSupplierBuilderImpl;
import java.util.function.Consumer;
import java.util.function.Function;

@Keep
public abstract class PartitionMappingSupplier extends MappingSupplier<PartitionMappingSupplier> {

  public static Builder builder() {
    return new PartitionMappingSupplierBuilderImpl();
  }

  @Keep
  public abstract static class Builder
      extends MappingSupplierBuilder<PartitionMappingSupplier, Builder> {

    /***
     * Sets the serialized metadata that was obtained when partitioning.
     *
     * @param metadata the serialized metadata
     */
    public abstract Builder setMetadata(byte[] metadata);

    /***
     * Callback to be notified of a partition that is later going to be needed. When all needed
     * partitions has been found, the callback specified to {@code setPrepareNewPartitions} will
     * be called.
     *
     * @param partitionToFetchConsumer the consumer to get keys for partitions.
     */
    public abstract Builder setPartitionToFetchConsumer(Consumer<String> partitionToFetchConsumer);

    /***
     * A callback notifying that all partitions should be prepared.
     *
     * @param run the callback to listen for when partitions should be prepared.
     */
    public abstract Builder setPrepareNewPartitions(Runnable run);

    /***
     * Set the partition supplier that is needed for retracing. All partitions needed has been
     * declared earlier and this should block until the bytes associated with the partition is
     * ready.
     *
     * @param supplier the function to return a partition to retrace
     */
    public abstract Builder setPartitionSupplier(Function<String, byte[]> supplier);
  }
}
