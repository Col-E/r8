// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.mappinginformation.MappingInformation;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetraceThrownExceptionElement;
import com.android.tools.r8.retrace.RetraceThrownExceptionResult;
import com.android.tools.r8.retrace.RetracedSourceFile;
import java.util.stream.Stream;

public class RetraceThrownExceptionResultImpl implements RetraceThrownExceptionResult {

  @SuppressWarnings("UnusedVariable")
  private final RetraceStackTraceContextImpl context;

  private final ClassReference obfuscatedReference;
  private final ClassNamingForNameMapper mapper;

  RetraceThrownExceptionResultImpl(
      RetraceStackTraceContextImpl context,
      ClassReference obfuscatedReference,
      ClassNamingForNameMapper mapper) {
    this.context = context;
    this.obfuscatedReference = obfuscatedReference;
    this.mapper = mapper;
  }

  @Override
  public Stream<RetraceThrownExceptionElement> stream() {
    return Stream.of(createElement());
  }

  @Override
  public boolean isEmpty() {
    return obfuscatedReference == null;
  }

  private RetraceThrownExceptionElement createElement() {
    return new RetraceThrownExceptionElementImpl(
        this,
        RetracedClassReferenceImpl.create(
            mapper == null ? obfuscatedReference : Reference.classFromTypeName(mapper.originalName),
            mapper != null),
        mapper,
        obfuscatedReference);
  }

  public static class RetraceThrownExceptionElementImpl implements RetraceThrownExceptionElement {

    private final RetraceThrownExceptionResultImpl thrownExceptionResult;
    private final RetracedClassReferenceImpl classReference;
    private final ClassNamingForNameMapper mapper;
    private final ClassReference thrownException;

    private RetraceThrownExceptionElementImpl(
        RetraceThrownExceptionResultImpl thrownExceptionResult,
        RetracedClassReferenceImpl classReference,
        ClassNamingForNameMapper mapper,
        ClassReference thrownException) {
      this.thrownExceptionResult = thrownExceptionResult;
      this.classReference = classReference;
      this.mapper = mapper;
      this.thrownException = thrownException;
    }

    @Override
    public RetracedClassReferenceImpl getRetracedClass() {
      return classReference;
    }

    @Override
    public RetraceThrownExceptionResult getParentResult() {
      return thrownExceptionResult;
    }

    @Override
    public RetracedSourceFile getSourceFile() {
      String sourceFile = null;
      if (mapper != null) {
        for (MappingInformation info : mapper.getAdditionalMappingInfo()) {
          if (info.isFileNameInformation()) {
            sourceFile = info.asFileNameInformation().getFileName();
            break;
          }
        }
      }
      return new RetracedSourceFileImpl(getRetracedClass(), sourceFile);
    }

    @Override
    public boolean isCompilerSynthesized() {
      return false;
    }

    @Override
    public RetraceStackTraceContext getContext() {
      return RetraceStackTraceContextImpl.builder().setThrownException(thrownException).build();
    }
  }
}
