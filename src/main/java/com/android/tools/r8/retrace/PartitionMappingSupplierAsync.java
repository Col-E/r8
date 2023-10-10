// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Keep;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.retrace.internal.PartitionMappingSupplierBase;

@Keep
public class PartitionMappingSupplierAsync
    extends PartitionMappingSupplierBase<PartitionMappingSupplierAsync>
    implements MappingSupplierAsync<PartitionMappingSupplierAsync> {

  private PartitionMappingSupplierAsync(
      RegisterMappingPartitionCallback registerCallback,
      PrepareMappingPartitionsCallback prepareCallback,
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
  }

  /***
   * Register an allowed mapping lookup to allow for prefetching of resources.
   *
   * @param classReference The minified class reference allowed to be lookup up.
   */
  @Keep
  @Override
  public PartitionMappingSupplierAsync registerClassUse(
      DiagnosticsHandler diagnosticsHandler, ClassReference classReference) {
    return super.registerClassUse(diagnosticsHandler, classReference);
  }

  /***
   * Register an allowed mapping lookup to allow for prefetching of resources.
   *
   * @param methodReference The minified method reference allowed to be lookup up.
   */
  @Keep
  @Override
  public PartitionMappingSupplierAsync registerMethodUse(
      DiagnosticsHandler diagnosticsHandler, MethodReference methodReference) {
    return super.registerMethodUse(diagnosticsHandler, methodReference);
  }

  /***
   * Register an allowed mapping lookup to allow for prefetching of resources.
   *
   * @param fieldReference The minified field reference allowed to be lookup up.
   */
  @Keep
  @Override
  public PartitionMappingSupplierAsync registerFieldUse(
      DiagnosticsHandler diagnosticsHandler, FieldReference fieldReference) {
    return super.registerFieldUse(diagnosticsHandler, fieldReference);
  }

  @Override
  public PartitionMappingSupplierAsync self() {
    return this;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public Retracer createRetracer(
      DiagnosticsHandler diagnosticsHandler, MappingPartitionFromKeySupplier supplier) {
    return createRetracerFromPartitionSupplier(diagnosticsHandler, supplier);
  }

  @Keep
  public static class Builder extends PartitionMappingSupplierBuilderBase<Builder> {

    private byte[] metadata;

    private Builder() {
      super(MapVersion.MAP_VERSION_NONE);
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

    @Override
    protected Builder self() {
      return this;
    }

    public PartitionMappingSupplierAsync build() {
      if (metadata == null) {
        throw new RuntimeException("Cannot build without providing metadata.");
      }
      return new PartitionMappingSupplierAsync(
          registerCallback,
          prepareCallback,
          finishedCallback,
          allowExperimental,
          metadata,
          fallbackMapVersion);
    }
  }
}
