// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import java.util.List;
import java.util.Optional;

@Keep
public interface RetraceClassElement extends RetraceElement<RetraceClassResult> {

  RetracedClassReference getRetracedClass();

  RetracedSourceFile getSourceFile();

  RetraceFieldResult lookupField(String fieldName);

  RetraceMethodResult lookupMethod(String methodName);

  RetraceFrameResult lookupFrame(Optional<Integer> position, String methodName);

  RetraceFrameResult lookupFrame(
      Optional<Integer> position,
      String methodName,
      List<TypeReference> formalTypes,
      TypeReference returnType);

  RetraceFrameResult lookupFrame(Optional<Integer> position, MethodReference methodReference);

  RetraceUnknownJsonMappingInformationResult getUnknownJsonMappingInformation();

  RetraceStackTraceContext getContextWhereClassWasThrown();
}
