// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.LineReader;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.retrace.InvalidMappingFileException;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.ProguardMappingProvider;
import com.android.tools.r8.retrace.internal.ProguardMapReaderWithFiltering.ProguardMapReaderWithFilteringInputBuffer;
import com.android.tools.r8.retrace.internal.ProguardMapReaderWithFiltering.ProguardMapReaderWithFilteringMappedBuffer;
import java.util.HashSet;
import java.util.Set;

public class ProguardMappingProviderBuilderImpl extends ProguardMappingProvider.Builder {

  private ProguardMapProducer proguardMapProducer;
  private boolean allowExperimental = false;
  private Set<String> allowedLookup = new HashSet<>();
  private boolean allowLookupAllClasses = false;
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
  public ProguardMappingProvider.Builder registerUse(ClassReference classReference) {
    allowedLookup.add(classReference.getTypeName());
    return self();
  }

  @Override
  public ProguardMappingProvider.Builder allowLookupAllClasses() {
    allowLookupAllClasses = true;
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
      Set<String> buildForClass = allowLookupAllClasses ? null : allowedLookup;
      LineReader reader =
          proguardMapProducer.isFileBacked()
              ? new ProguardMapReaderWithFilteringMappedBuffer(
                  proguardMapProducer.getPath(), buildForClass)
              : new ProguardMapReaderWithFilteringInputBuffer(
                  proguardMapProducer.get(), buildForClass);
      return new ProguardMappingProviderImpl(
          ClassNameMapper.mapperFromLineReaderWithFiltering(
              reader, diagnosticsHandler, true, allowExperimental));
    } catch (Exception e) {
      throw new InvalidMappingFileException(e);
    }
  }
}
