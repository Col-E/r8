// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.retrace.InvalidMappingFileException;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.ProguardMappingProvider;
import java.io.BufferedReader;

public class ProguardMappingProviderBuilderImpl extends ProguardMappingProvider.Builder {

  private ProguardMapProducer proguardMapProducer;
  private boolean allowExperimental = false;
  private DiagnosticsHandler diagnosticsHandler = new DiagnosticsHandler() {};

  @Override
  public ProguardMappingProvider.Builder self() {
    return this;
  }

  @Override
  public ProguardMappingProvider.Builder setAllowExperimental(boolean allowExperimental) {
    this.allowExperimental = allowExperimental;
    return self();
  }

  @Override
  public ProguardMappingProvider.Builder setDiagnosticsHandler(
      DiagnosticsHandler diagnosticsHandler) {
    this.diagnosticsHandler = diagnosticsHandler;
    return self();
  }

  @Override
  public ProguardMappingProvider.Builder setProguardMapProducer(
      ProguardMapProducer proguardMapProducer) {
    this.proguardMapProducer = proguardMapProducer;
    return self();
  }

  @Override
  public ProguardMappingProvider build() {
    try {
      return new ProguardMappingProviderImpl(
          ClassNameMapper.mapperFromBufferedReader(
              new BufferedReader(proguardMapProducer.get()),
              diagnosticsHandler,
              true,
              allowExperimental));
    } catch (Exception e) {
      throw new InvalidMappingFileException(e);
    }
  }
}
