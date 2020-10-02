// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;

/** A default implementation for the retrace api using the ClassNameMapper defined in R8. */
@Keep
public class Retracer implements RetraceApi {

  private final ClassNameMapper classNameMapper;

  private Retracer(ClassNameMapper classNameMapper) {
    this.classNameMapper = classNameMapper;
    assert classNameMapper != null;
  }

  public static RetraceApi create(ClassNameMapper classNameMapper) {
    return new Retracer(classNameMapper);
  }

  @Override
  public RetraceMethodResult retrace(MethodReference methodReference) {
    return retrace(methodReference.getHolderClass()).lookupMethod(methodReference.getMethodName());
  }

  @Override
  public RetraceFieldResult retrace(FieldReference fieldReference) {
    return retrace(fieldReference.getHolderClass()).lookupField(fieldReference.getFieldName());
  }

  @Override
  public RetraceClassResult retrace(ClassReference classReference) {
    return RetraceClassResult.create(
        classReference, classNameMapper.getClassNaming(classReference.getTypeName()), this);
  }

  @Override
  public RetraceTypeResult retrace(TypeReference typeReference) {
    return new RetraceTypeResult(typeReference, this);
  }
}
