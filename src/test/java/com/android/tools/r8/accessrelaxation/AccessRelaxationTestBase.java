// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPublic;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;

abstract class AccessRelaxationTestBase extends TestBase {

  final TestParameters parameters;

  AccessRelaxationTestBase(TestParameters parameters) {
    this.parameters = parameters;
  }

  static void assertPublic(CodeInspector codeInspector, Class clazz, MethodSignature signature) {
    ClassSubject classSubject = codeInspector.clazz(clazz);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.method(signature);
    assertThat(methodSubject, isPresent());
    assertThat(methodSubject, isPublic());
  }

  static void assertNotPublic(CodeInspector codeInspector, Class clazz, MethodSignature signature) {
    ClassSubject classSubject = codeInspector.clazz(clazz);
    assertThat(classSubject, isPresent());
    MethodSubject methodSubject = classSubject.method(signature);
    assertThat(methodSubject, isPresent());
    assertThat(methodSubject, not(isPublic()));
  }

}
