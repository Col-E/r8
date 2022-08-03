// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.LineReader;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.ProguardMapSupplier.ProguardMapChecker;
import com.android.tools.r8.naming.ProguardMapSupplier.ProguardMapChecker.VerifyMappingFileHashResult;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.retrace.InvalidMappingFileException;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.ProguardMappingSupplier;
import com.android.tools.r8.retrace.internal.ProguardMapReaderWithFiltering.ProguardMapReaderWithFilteringInputBuffer;
import com.android.tools.r8.retrace.internal.ProguardMapReaderWithFiltering.ProguardMapReaderWithFilteringMappedBuffer;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * IntelliJ highlights the class as being invalid because it cannot see getClassNameMapper is
 * defined on the class for some reason.
 */
public class ProguardMappingSupplierImpl extends ProguardMappingSupplier {

  private ProguardMapProducer proguardMapProducer;
  private final boolean allowExperimental;
  private final boolean loadAllDefinitions;

  private ClassNameMapper classNameMapper;
  private final Set<String> pendingClassMappings = new HashSet<>();
  private final Set<String> builtClassMappings = new HashSet<>();

  public ProguardMappingSupplierImpl(ClassNameMapper classNameMapper) {
    this.classNameMapper = classNameMapper;
    this.proguardMapProducer = null;
    this.allowExperimental = true;
    this.loadAllDefinitions = true;
  }

  ProguardMappingSupplierImpl(
      ProguardMapProducer proguardMapProducer,
      boolean allowExperimental,
      boolean loadAllDefinitions) {
    this.proguardMapProducer = proguardMapProducer;
    this.allowExperimental = allowExperimental;
    this.loadAllDefinitions = loadAllDefinitions;
  }

  private boolean hasClassMappingFor(String typeName) {
    return loadAllDefinitions || builtClassMappings.contains(typeName);
  }

  @Override
  Set<MapVersionMappingInformation> getMapVersions(DiagnosticsHandler diagnosticsHandler) {
    return getClassNameMapper(diagnosticsHandler).getMapVersions();
  }

  @Override
  ClassNamingForNameMapper getClassNaming(DiagnosticsHandler diagnosticsHandler, String typeName) {
    if (!hasClassMappingFor(typeName)) {
      pendingClassMappings.add(typeName);
    }
    return getClassNameMapper(diagnosticsHandler).getClassNaming(typeName);
  }

  @Override
  String getSourceFileForClass(DiagnosticsHandler diagnosticsHandler, String typeName) {
    return getClassNameMapper(diagnosticsHandler).getSourceFile(typeName);
  }

  private ClassNameMapper getClassNameMapper(DiagnosticsHandler diagnosticsHandler) {
    if (proguardMapProducer == null) {
      assert classNameMapper != null;
      return classNameMapper;
    }
    if (classNameMapper != null && pendingClassMappings.isEmpty()) {
      return classNameMapper;
    }
    try {
      Predicate<String> buildForClass = loadAllDefinitions ? null : pendingClassMappings::contains;
      boolean readPreambleAndSourceFile = classNameMapper == null;
      LineReader reader =
          proguardMapProducer.isFileBacked()
              ? new ProguardMapReaderWithFilteringMappedBuffer(
                  proguardMapProducer.getPath(), buildForClass, readPreambleAndSourceFile)
              : new ProguardMapReaderWithFilteringInputBuffer(
                  proguardMapProducer.get(), buildForClass, readPreambleAndSourceFile);
      ClassNameMapper classNameMapper =
          ClassNameMapper.mapperFromLineReaderWithFiltering(
              reader, getMapVersion(), diagnosticsHandler, true, allowExperimental);
      this.classNameMapper = classNameMapper.combine(this.classNameMapper);
      builtClassMappings.addAll(pendingClassMappings);
      pendingClassMappings.clear();
      if (loadAllDefinitions) {
        proguardMapProducer = null;
      }
    } catch (Exception e) {
      throw new InvalidMappingFileException(e);
    }
    return classNameMapper;
  }

  private MapVersion getMapVersion() {
    if (classNameMapper == null) {
      return MapVersion.MAP_VERSION_NONE;
    } else {
      MapVersionMappingInformation mapVersion = classNameMapper.getFirstMapVersionInformation();
      return mapVersion == null ? MapVersion.MAP_VERSION_UNKNOWN : mapVersion.getMapVersion();
    }
  }

  @Override
  public ProguardMappingSupplier registerClassUse(
      DiagnosticsHandler diagnosticsHandler, ClassReference classReference) {
    if (!hasClassMappingFor(classReference.getTypeName())) {
      pendingClassMappings.add(classReference.getTypeName());
    }
    return this;
  }

  @Override
  public void verifyMappingFileHash(DiagnosticsHandler diagnosticsHandler) {
    try (InputStream reader = proguardMapProducer.get()) {
      VerifyMappingFileHashResult checkResult =
          ProguardMapChecker.validateProguardMapHash(
              CharStreams.toString(new InputStreamReader(reader, StandardCharsets.UTF_8)));
      if (checkResult.isError()) {
        diagnosticsHandler.error(new StringDiagnostic(checkResult.getMessage()));
        throw new RuntimeException(checkResult.getMessage());
      }
      if (!checkResult.isOk()) {
        diagnosticsHandler.warning(new StringDiagnostic(checkResult.getMessage()));
      }
    } catch (IOException e) {
      diagnosticsHandler.error(new ExceptionDiagnostic(e));
      throw new RuntimeException(e);
    }
  }
}
