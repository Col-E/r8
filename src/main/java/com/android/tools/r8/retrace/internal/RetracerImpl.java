// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.InvalidMappingFileException;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.RetraceFrameResult;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.Retracer;
import java.io.BufferedReader;

/** A default implementation for the retrace api using the ClassNameMapper defined in R8. */
public class RetracerImpl implements Retracer {

  private final ClassNameMapper classNameMapper;
  private final DiagnosticsHandler diagnosticsHandler;

  public RetracerImpl(ClassNameMapper classNameMapper, DiagnosticsHandler diagnosticsHandler) {
    this.classNameMapper = classNameMapper;
    this.diagnosticsHandler = diagnosticsHandler;
    assert classNameMapper != null;
  }

  public static RetracerImpl create(
      ProguardMapProducer proguardMapProducer,
      DiagnosticsHandler diagnosticsHandler,
      boolean allowExperimentalMapping) {
    if (proguardMapProducer instanceof DirectClassNameMapperProguardMapProducer) {
      return new RetracerImpl(
          ((DirectClassNameMapperProguardMapProducer) proguardMapProducer).getClassNameMapper(),
          diagnosticsHandler);
    }
    try {
      ClassNameMapper classNameMapper =
          ClassNameMapper.mapperFromBufferedReader(
              new BufferedReader(proguardMapProducer.get()),
              diagnosticsHandler,
              true,
              allowExperimentalMapping);
      return new RetracerImpl(classNameMapper, diagnosticsHandler);
    } catch (Throwable throwable) {
      throw new InvalidMappingFileException(throwable);
    }
  }

  public DiagnosticsHandler getDiagnosticsHandler() {
    return diagnosticsHandler;
  }

  @Override
  public RetraceMethodResultImpl retraceMethod(MethodReference methodReference) {
    return retraceClass(methodReference.getHolderClass())
        .lookupMethod(methodReference.getMethodName());
  }

  @Override
  public RetraceFrameResult retraceFrame(MethodReference methodReference, int position) {
    return retraceFrame(methodReference, position, RetraceStackTraceContext.getInitialContext());
  }

  @Override
  public RetraceFrameResult retraceFrame(
      MethodReference methodReference, int position, RetraceStackTraceContext context) {
    return retraceClass(methodReference.getHolderClass())
        .lookupMethod(methodReference.getMethodName())
        .narrowByPosition(position);
  }

  @Override
  public RetraceFieldResultImpl retraceField(FieldReference fieldReference) {
    return retraceClass(fieldReference.getHolderClass()).lookupField(fieldReference.getFieldName());
  }

  @Override
  public RetraceClassResultImpl retraceClass(ClassReference classReference) {
    return RetraceClassResultImpl.create(
        classReference, classNameMapper.getClassNaming(classReference.getTypeName()), this);
  }

  @Override
  public RetraceTypeResultImpl retraceType(TypeReference typeReference) {
    return RetraceTypeResultImpl.create(typeReference, this);
  }
}
