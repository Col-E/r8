// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.retrace.internal.ProguardMappingSupplierBuilderImpl;

@Keep
public abstract class ProguardMappingSupplier extends MappingSupplier<ProguardMappingSupplier> {

  public static Builder builder() {
    return new ProguardMappingSupplierBuilderImpl();
  }

  @Keep
  public abstract static class Builder
      extends MappingSupplierBuilder<ProguardMappingSupplier, Builder> {

    public abstract Builder setProguardMapProducer(ProguardMapProducer proguardMapProducer);

    public abstract Builder setLoadAllDefinitions(boolean loadAllDefinitions);
  }
}
