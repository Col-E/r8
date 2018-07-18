// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexClass;
import java.util.List;
import java.util.function.Consumer;

public class AbsentClassSubject extends ClassSubject {

  @Override
  public boolean isPresent() {
    return false;
  }

  @Override
  public void forAllMethods(Consumer<FoundMethodSubject> inspection) {}

  @Override
  public MethodSubject method(String returnType, String name, List<String> parameters) {
    return new AbsentMethodSubject();
  }

  @Override
  public void forAllFields(Consumer<FoundFieldSubject> inspection) {}

  @Override
  public FieldSubject field(String type, String name) {
    return new AbsentFieldSubject();
  }

  @Override
  public boolean isAbstract() {
    return false;
  }

  @Override
  public boolean isAnnotation() {
    return false;
  }

  @Override
  public DexClass getDexClass() {
    return null;
  }

  @Override
  public AnnotationSubject annotation(String name) {
    return new AbsentAnnotationSubject();
  }

  @Override
  public String getOriginalName() {
    return null;
  }

  @Override
  public String getOriginalDescriptor() {
    return null;
  }

  @Override
  public String getFinalName() {
    return null;
  }

  @Override
  public String getFinalDescriptor() {
    return null;
  }

  @Override
  public boolean isRenamed() {
    return false;
  }

  @Override
  public boolean isMemberClass() {
    return false;
  }

  @Override
  public boolean isLocalClass() {
    return false;
  }

  @Override
  public boolean isAnonymousClass() {
    return false;
  }

  @Override
  public boolean isSynthesizedJavaLambdaClass() {
    return false;
  }

  @Override
  public String getOriginalSignatureAttribute() {
    return null;
  }

  @Override
  public String getFinalSignatureAttribute() {
    return null;
  }
}
