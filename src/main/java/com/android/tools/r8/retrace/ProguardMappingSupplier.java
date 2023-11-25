// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.retrace.internal.ProguardMappingSupplierBuilderImpl;

@KeepForApi
public abstract class ProguardMappingSupplier implements MappingSupplier<ProguardMappingSupplier> {

  public static Builder builder() {
    return new ProguardMappingSupplierBuilderImpl();
  }

  @KeepForApi
  public abstract static class Builder
      extends MappingSupplierBuilder<ProguardMappingSupplier, Builder> {

    public abstract Builder setProguardMapProducer(ProguardMapProducer proguardMapProducer);

    public abstract Builder setLoadAllDefinitions(boolean loadAllDefinitions);
  }
}
