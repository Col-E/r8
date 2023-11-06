// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.retrace.internal.PartitionMappingSupplierBase;

@KeepForApi
public class PartitionMappingSupplier extends PartitionMappingSupplierBase<PartitionMappingSupplier>
    implements MappingSupplier<PartitionMappingSupplier> {

  private final MappingPartitionFromKeySupplier partitionSupplier;

  private PartitionMappingSupplier(
      RegisterMappingPartitionCallback registerCallback,
      PrepareMappingPartitionsCallback prepareCallback,
      MappingPartitionFromKeySupplier partitionSupplier,
      FinishedPartitionMappingCallback finishedCallback,
      boolean allowExperimental,
      byte[] metadata,
      MapVersion fallbackMapVersion) {
    super(
        registerCallback,
        prepareCallback,
        finishedCallback,
        allowExperimental,
        metadata,
        fallbackMapVersion);
    this.partitionSupplier = partitionSupplier;
  }

  /***
   * Register an allowed mapping lookup to allow for prefetching of resources.
   *
   * @param classReference The minified class reference allowed to be lookup up.
   */
  @KeepForApi
  @Override
  public PartitionMappingSupplier registerClassUse(
      DiagnosticsHandler diagnosticsHandler, ClassReference classReference) {
    return super.registerClassUse(diagnosticsHandler, classReference);
  }

  /***
   * Register an allowed mapping lookup to allow for prefetching of resources.
   *
   * @param methodReference The minified method reference allowed to be lookup up.
   */
  @KeepForApi
  @Override
  public PartitionMappingSupplier registerMethodUse(
      DiagnosticsHandler diagnosticsHandler, MethodReference methodReference) {
    return super.registerMethodUse(diagnosticsHandler, methodReference);
  }

  /***
   * Register an allowed mapping lookup to allow for prefetching of resources.
   *
   * @param fieldReference The minified field reference allowed to be lookup up.
   */
  @KeepForApi
  @Override
  public PartitionMappingSupplier registerFieldUse(
      DiagnosticsHandler diagnosticsHandler, FieldReference fieldReference) {
    return super.registerFieldUse(diagnosticsHandler, fieldReference);
  }

  @Override
  public Retracer createRetracer(DiagnosticsHandler diagnosticsHandler) {
    return createRetracerFromPartitionSupplier(diagnosticsHandler, partitionSupplier);
  }

  public MappingPartitionFromKeySupplier getMappingPartitionFromKeySupplier() {
    return partitionSupplier;
  }

  @Override
  public PartitionMappingSupplier getPartitionMappingSupplier() {
    return this;
  }

  @Override
  public PartitionMappingSupplier self() {
    return this;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static NoMetadataBuilder noMetadataBuilder(MapVersion mapVersion) {
    return new NoMetadataBuilder(mapVersion);
  }

  @KeepForApi
  public abstract static class NoMetadataBuilderBase<B extends NoMetadataBuilderBase<B>>
      extends PartitionMappingSupplierBuilderBase<B> {

    protected MappingPartitionFromKeySupplier partitionSupplier;

    private NoMetadataBuilderBase(MapVersion fallbackMapVersion) {
      super(fallbackMapVersion);
    }

    /***
     * Sets the partition supplier.
     *
     * @param partitionSupplier the supplier of partitions for requested keys.
     */
    public B setMappingPartitionFromKeySupplier(MappingPartitionFromKeySupplier partitionSupplier) {
      this.partitionSupplier = partitionSupplier;
      return self();
    }
  }

  @KeepForApi
  public static class NoMetadataBuilder extends NoMetadataBuilderBase<NoMetadataBuilder> {

    private NoMetadataBuilder(MapVersion fallbackMapVersion) {
      super(fallbackMapVersion);
    }

    @Override
    protected NoMetadataBuilder self() {
      return this;
    }

    public PartitionMappingSupplier build() {
      if (partitionSupplier == null) {
        throw new RuntimeException("Cannot build without providing a partition supplier.");
      }
      return new PartitionMappingSupplier(
          registerCallback,
          prepareCallback,
          partitionSupplier,
          finishedCallback,
          allowExperimental,
          null,
          fallbackMapVersion);
    }
  }

  @KeepForApi
  public static class Builder extends NoMetadataBuilderBase<Builder> {

    private byte[] metadata;

    private Builder() {
      super(MapVersion.MAP_VERSION_NONE);
    }

    @Override
    protected Builder self() {
      return this;
    }

    /***
     * Sets the serialized metadata that was obtained when partitioning.
     *
     * @param metadata the serialized metadata
     */
    public Builder setMetadata(byte[] metadata) {
      this.metadata = metadata;
      return self();
    }

    public PartitionMappingSupplier build() {
      if (partitionSupplier == null) {
        throw new RuntimeException("Cannot build without providing a partition supplier");
      }
      if (metadata == null) {
        throw new RuntimeException("Cannot build without providing metadata.");
      }
      return new PartitionMappingSupplier(
          registerCallback,
          prepareCallback,
          partitionSupplier,
          finishedCallback,
          allowExperimental,
          metadata,
          fallbackMapVersion);
    }
  }
}
