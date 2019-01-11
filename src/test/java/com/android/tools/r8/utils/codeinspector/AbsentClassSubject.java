// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
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
  public MethodSubject uniqueMethodWithName(String name) {
    return new AbsentMethodSubject();
  }

  @Override
  public void forAllFields(Consumer<FoundFieldSubject> inspection) {}

  @Override
  public FieldSubject field(String type, String name) {
    return new AbsentFieldSubject();
  }

  @Override
  public FieldSubject uniqueFieldWithName(String name) {
    return new AbsentFieldSubject();
  }

  @Override
  public boolean isAbstract() {
    throw new Unreachable("Cannot determine if an absent class is abstract");
  }

  @Override
  public boolean isAnnotation() {
    throw new Unreachable("Cannot determine if an absent class is an annotation");
  }

  @Override
  public boolean isPublic() {
    throw new Unreachable("Cannot determine if an absent class is public");
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
    throw new Unreachable("Cannot determine if an absent class has been renamed");
  }

  @Override
  public boolean isMemberClass() {
    throw new Unreachable("Cannot determine if an absent class is a member class");
  }

  @Override
  public boolean isLocalClass() {
    throw new Unreachable("Cannot determine if an absent class is a local class");
  }

  @Override
  public boolean isAnonymousClass() {
    throw new Unreachable("Cannot determine if an absent class is an anonymous class");
  }

  @Override
  public boolean isSynthetic() {
    throw new Unreachable("Cannot determine if an absent class is synthetic");
  }

  @Override
  public boolean isSynthesizedJavaLambdaClass() {
    throw new Unreachable("Cannot determine if an absent class is a synthesized lambda class");
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
