// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import java.util.List;
import java.util.OptionalInt;

@KeepForApi
public interface RetraceClassElement extends RetraceElement<RetraceClassResult> {

  RetracedClassReference getRetracedClass();

  RetracedSourceFile getSourceFile();

  RetraceFieldResult lookupField(String fieldName);

  RetraceMethodResult lookupMethod(String methodName);

  RetraceFrameResult lookupFrame(
      RetraceStackTraceContext context, OptionalInt position, String methodName);

  RetraceFrameResult lookupFrame(
      RetraceStackTraceContext context,
      OptionalInt position,
      String methodName,
      List<TypeReference> formalTypes,
      TypeReference returnType);

  RetraceFrameResult lookupFrame(
      RetraceStackTraceContext context, OptionalInt position, MethodReference methodReference);

  RetraceUnknownJsonMappingInformationResult getUnknownJsonMappingInformation();
}
