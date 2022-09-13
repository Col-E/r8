// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.RetraceFrameResult;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.Retracer;
import java.util.OptionalInt;

/** A default implementation for the retrace api using the ClassNameMapper defined in R8. */
public class RetracerImpl implements Retracer {

  private final MappingSupplierInternal classNameMapperSupplier;
  private final DiagnosticsHandler diagnosticsHandler;

  private RetracerImpl(
      MappingSupplierInternal classNameMapperSupplier, DiagnosticsHandler diagnosticsHandler) {
    this.classNameMapperSupplier = classNameMapperSupplier;
    this.diagnosticsHandler = diagnosticsHandler;
    assert classNameMapperSupplier != null;
  }

  public DiagnosticsHandler getDiagnosticsHandler() {
    return diagnosticsHandler;
  }

  @Override
  public RetraceMethodResultImpl retraceMethod(MethodReference methodReference) {
    return retraceClass(methodReference.getHolderClass()).lookupMethodInternal(methodReference);
  }

  @Override
  public RetraceFrameResult retraceFrame(
      RetraceStackTraceContext context,
      OptionalInt position,
      ClassReference classReference,
      String methodName) {
    return retraceClass(classReference).lookupFrame(context, position, methodName);
  }

  @Override
  public RetraceFrameResult retraceFrame(
      RetraceStackTraceContext context, OptionalInt position, MethodReference methodReference) {
    return retraceClass(methodReference.getHolderClass())
        .lookupFrame(
            context,
            position,
            methodReference.getMethodName(),
            methodReference.getFormalTypes(),
            methodReference.getReturnType());
  }

  @Override
  public RetraceFieldResultImpl retraceField(FieldReference fieldReference) {
    return retraceClass(fieldReference.getHolderClass()).lookupFieldInternal(fieldReference);
  }

  @Override
  public RetraceClassResultImpl retraceClass(ClassReference classReference) {
    return RetraceClassResultImpl.create(
        classReference, classNameMapperSupplier.getClassNaming(classReference.getTypeName()), this);
  }

  @Override
  public RetraceTypeResultImpl retraceType(TypeReference typeReference) {
    return RetraceTypeResultImpl.create(typeReference, this);
  }

  @Override
  public RetraceThrownExceptionResultImpl retraceThrownException(ClassReference exception) {
    return retraceClass(exception).lookupThrownException(RetraceStackTraceContext.empty());
  }

  public String getSourceFile(ClassReference classReference) {
    return classNameMapperSupplier.getSourceFileForClass(classReference.getTypeName());
  }

  public static RetracerImpl createInternal(
      MappingSupplierInternal classNameMapperSupplier, DiagnosticsHandler diagnosticsHandler) {
    return new RetracerImpl(classNameMapperSupplier, diagnosticsHandler);
  }
}
