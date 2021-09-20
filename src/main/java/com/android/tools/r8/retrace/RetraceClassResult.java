// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.TypeReference;
import java.util.List;
import java.util.Optional;

@Keep
public interface RetraceClassResult extends RetraceResult<RetraceClassElement> {

  boolean hasRetraceResult();

  RetraceFieldResult lookupField(String fieldName);

  RetraceFieldResult lookupField(String fieldName, TypeReference fieldType);

  RetraceMethodResult lookupMethod(String methodName);

  RetraceMethodResult lookupMethod(
      String methodName, List<TypeReference> formalTypes, TypeReference returnType);

  RetraceFrameResult lookupFrame(
      RetraceStackTraceContext context, Optional<Integer> position, String methodName);

  RetraceFrameResult lookupFrame(
      RetraceStackTraceContext context,
      Optional<Integer> position,
      String methodName,
      List<TypeReference> formalTypes,
      TypeReference returnType);
}
