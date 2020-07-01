// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.kotlin.AbstractR8KotlinTestBase;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;

public abstract class AbstractR8KotlinNamingTestBase extends AbstractR8KotlinTestBase {

  protected final boolean minification;

  AbstractR8KotlinNamingTestBase(
      KotlinTargetVersion kotlinTargetVersion,
      boolean allowAccessModification,
      boolean minification) {
    super(kotlinTargetVersion, allowAccessModification);
    this.minification = minification;
  }

  protected ClassSubject checkClassIsRenamed(CodeInspector inspector, String className) {
    ClassSubject classSubject = inspector.clazz(className);
    assertThat(classSubject, isPresentAndRenamed());
    return classSubject;
  }

  protected ClassSubject checkClassIsNotRenamed(CodeInspector inspector, String className) {
    ClassSubject classSubject = inspector.clazz(className);
    assertThat(classSubject, isPresentAndNotRenamed());
    return classSubject;
  }

  protected FieldSubject checkFieldIsRenamed(
      ClassSubject classSubject, String fieldType, String fieldName) {
    FieldSubject fieldSubject = checkFieldIsKept(classSubject, fieldType, fieldName);
    assertThat(fieldSubject, isPresentAndRenamed());
    return fieldSubject;
  }

  protected FieldSubject checkFieldIsRenamed(ClassSubject classSubject, String fieldName) {
    FieldSubject fieldSubject = checkFieldIsKept(classSubject, fieldName);
    assertThat(fieldSubject, isPresentAndRenamed());
    return fieldSubject;
  }

  protected FieldSubject checkFieldIsNotRenamed(
      ClassSubject classSubject, String fieldType, String fieldName) {
    FieldSubject fieldSubject = checkFieldIsKept(classSubject, fieldType, fieldName);
    assertThat(fieldSubject, isPresentAndNotRenamed());
    return fieldSubject;
  }

  protected FieldSubject checkFieldIsNotRenamed(ClassSubject classSubject, String fieldName) {
    FieldSubject fieldSubject = checkFieldIsKept(classSubject, fieldName);
    assertThat(fieldSubject, isPresentAndNotRenamed());
    return fieldSubject;
  }

  protected MethodSubject checkMethodIsRenamed(
      ClassSubject classSubject, MethodSignature methodSignature) {
    MethodSubject methodSubject = checkMethodIsKept(classSubject, methodSignature);
    assertThat(methodSubject, isPresentAndRenamed());
    return methodSubject;
  }

  protected MethodSubject checkMethodIsRenamed(ClassSubject classSubject, String methodName) {
    MethodSubject methodSubject = checkMethodIsKept(classSubject, methodName);
    assertThat(methodSubject, isPresentAndRenamed());
    return methodSubject;
  }

  protected MethodSubject checkMethodIsNotRenamed(
      ClassSubject classSubject, MethodSignature methodSignature) {
    MethodSubject methodSubject = checkMethodIsKept(classSubject, methodSignature);
    assertThat(methodSubject, isPresentAndNotRenamed());
    return methodSubject;
  }

  protected MethodSubject checkMethodIsNotRenamed(ClassSubject classSubject, String methodName) {
    MethodSubject methodSubject = checkMethodIsKept(classSubject, methodName);
    assertThat(methodSubject, isPresentAndNotRenamed());
    return methodSubject;
  }

}
