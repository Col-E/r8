// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.ProguardMappingSupplier;

public class ProguardMappingSupplierBuilderImpl extends ProguardMappingSupplier.Builder {

  private ProguardMapProducer proguardMapProducer;
  private boolean allowExperimental = false;
  private boolean loadAllDefinitions = true;

  @Override
  public ProguardMappingSupplier.Builder self() {
    return this;
  }

  @Override
  public ProguardMappingSupplier.Builder setAllowExperimental(boolean allowExperimental) {
    this.allowExperimental = allowExperimental;
    return self();
  }

  @Override
  public ProguardMappingSupplier.Builder setProguardMapProducer(
      ProguardMapProducer proguardMapProducer) {
    this.proguardMapProducer = proguardMapProducer;
    return self();
  }

  @Override
  public ProguardMappingSupplier.Builder setLoadAllDefinitions(boolean loadAllDefinitions) {
    this.loadAllDefinitions = loadAllDefinitions;
    return self();
  }

  @Override
  public ProguardMappingSupplier build() {
    return new ProguardMappingSupplierImpl(
        proguardMapProducer, allowExperimental, loadAllDefinitions);
  }
}
