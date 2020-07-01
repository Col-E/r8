// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.Box;

public class RetraceBaseImpl implements RetraceBase {

  private final ClassNameMapper classNameMapper;

  private RetraceBaseImpl(ClassNameMapper classNameMapper) {
    this.classNameMapper = classNameMapper;
  }

  public static RetraceBase create(ClassNameMapper classNameMapper) {
    return new RetraceBaseImpl(classNameMapper);
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
        classReference, classNameMapper.getClassNaming(classReference.getTypeName()));
  }

  @Override
  public RetraceSourceFileResult retraceSourceFile(
      ClassReference classReference, String sourceFile) {
    Box<RetraceSourceFileResult> retracedSourceFile = new Box<>();
    retrace(classReference)
        .forEach(element -> retracedSourceFile.set(element.retraceSourceFile(sourceFile)));
    return retracedSourceFile.get();
  }

  @Override
  public RetraceTypeResult retrace(TypeReference typeReference) {
    return new RetraceTypeResult(typeReference, this);
  }
}
