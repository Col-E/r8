// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.verticalclassmerging;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;

public class ImplementsMergedTypeIndirectlyTest extends MergedTypeBaseTest {

  static class TestClass implements J {

    public static void main(String[] args) {
      System.out.print("Hello world");
    }
  }

  public ImplementsMergedTypeIndirectlyTest(Backend backend, boolean enableVerticalClassMerging) {
    super(backend, enableVerticalClassMerging);
  }

  @Override
  public Class<?> getTestClass() {
    return TestClass.class;
  }

  @Override
  public String getAdditionalKeepRules() {
    // Keep interface J to prevent it from being merged into TestClass.
    return "-keep class **$J";
  }

  @Override
  public String getConditionForProguardIfRule() {
    // After class merging, J will no longer extend I (and therefore, TestClass will no longer
    // implement I indirectly), but we should still keep the class Unused in the output.
    return "-if class **$TestClass implements **$I";
  }

  @Override
  public String getExpectedStdout() {
    return "Hello world";
  }

  public void inspect(CodeInspector inspector) {
    super.inspect(inspector);

    // Verify that TestClass still implements J.
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertEquals(J.class.getTypeName(), testClassSubject.getDexClass().interfaces.toSourceString());
  }
}
