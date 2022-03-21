// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.MappingProvider;
import com.android.tools.r8.retrace.RetraceFrameResult;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.retrace.RetracerBuilder;
import java.util.OptionalInt;
import java.util.Set;

/** A default implementation for the retrace api using the ClassNameMapper defined in R8. */
public class RetracerImpl implements Retracer {

  private final ClassNameMapper classNameMapper;
  private final DiagnosticsHandler diagnosticsHandler;

  private RetracerImpl(ClassNameMapper classNameMapper, DiagnosticsHandler diagnosticsHandler) {
    this.classNameMapper = classNameMapper;
    this.diagnosticsHandler = diagnosticsHandler;
    assert classNameMapper != null;
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

  @Override
  public RetraceThrownExceptionResultImpl retraceThrownException(ClassReference exception) {
    return retraceClass(exception).lookupThrownException(RetraceStackTraceContext.empty());
  }

  public Set<MapVersionMappingInformation> getMapVersions() {
    return classNameMapper.getMapVersions();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder implements RetracerBuilder {

    private MappingProvider mappingProvider;
    private DiagnosticsHandler diagnosticsHandler = new DiagnosticsHandler() {};

    private Builder() {}

    @Override
    public Builder setMappingProvider(MappingProvider mappingProvider) {
      this.mappingProvider = mappingProvider;
      return this;
    }

    @Override
    public Builder setDiagnosticsHandler(DiagnosticsHandler diagnosticsHandler) {
      this.diagnosticsHandler = diagnosticsHandler;
      return this;
    }

    @Override
    public RetracerImpl build() {
      return new RetracerImpl(
          ((MappingProviderInternal) mappingProvider).getClassNameMapper(), diagnosticsHandler);
    }
  }
}
