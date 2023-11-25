// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.naming.MapVersion;

@KeepForApi
public abstract class PartitionMappingSupplierBuilderBase<
    T extends PartitionMappingSupplierBuilderBase<T>> {

  protected RegisterMappingPartitionCallback registerCallback =
      RegisterMappingPartitionCallback.empty();
  protected PrepareMappingPartitionsCallback prepareCallback =
      PrepareMappingPartitionsCallback.empty();
  protected FinishedPartitionMappingCallback finishedCallback =
      FinishedPartitionMappingCallback.empty();
  protected final MapVersion fallbackMapVersion;
  protected boolean allowExperimental = false;

  public PartitionMappingSupplierBuilderBase(MapVersion fallbackMapVersion) {
    this.fallbackMapVersion = fallbackMapVersion;
  }

  public T setRegisterMappingPartitionCallback(RegisterMappingPartitionCallback registerCallback) {
    this.registerCallback = registerCallback;
    return self();
  }

  public T setFinishedPartitionMappingCallback(FinishedPartitionMappingCallback finishedCallback) {
    this.finishedCallback = finishedCallback;
    return self();
  }

  public T setPrepareMappingPartitionsCallback(PrepareMappingPartitionsCallback prepareCallback) {
    this.prepareCallback = prepareCallback;
    return self();
  }

  public T setAllowExperimental(boolean allowExperimental) {
    this.allowExperimental = allowExperimental;
    return self();
  }

  protected abstract T self();
}
