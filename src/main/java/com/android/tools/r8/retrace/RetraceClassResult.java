// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.TypeReference;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Keep
public interface RetraceClassResult {

  RetraceFieldResult lookupField(String fieldName);

  RetraceFieldResult lookupField(String fieldName, TypeReference fieldType);

  RetraceMethodResult lookupMethod(String methodName);

  RetraceMethodResult lookupMethod(
      String methodName, List<TypeReference> formalTypes, TypeReference returnType);

  RetraceFrameResult lookupFrame(String methodName);

  RetraceFrameResult lookupFrame(String methodName, int position);

  RetraceFrameResult lookupFrame(
      String methodName, int position, List<TypeReference> formalTypes, TypeReference returnType);

  Stream<Element> stream();

  RetraceClassResult forEach(Consumer<Element> resultConsumer);

  boolean isAmbiguous();

  @Keep
  interface Element {

    RetracedClass getRetracedClass();

    RetraceClassResult getRetraceClassResult();

    RetraceSourceFileResult retraceSourceFile(String sourceFile);

    RetraceFieldResult lookupField(String fieldName);

    RetraceMethodResult lookupMethod(String methodName);

    RetraceFrameResult lookupFrame(String methodName);

    RetraceFrameResult lookupFrame(String methodName, int position);

    RetraceFrameResult lookupFrame(
        String methodName, int position, List<TypeReference> formalTypes, TypeReference returnType);
  }
}
