// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.verticalclassmerging;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;

public class ExtendsMergedTypeIndirectlyTest extends MergedTypeBaseTest {

  static class TestClass extends B {

    public static void main(String[] args) {
      // The instantiation of B prevents it from being merged into TestClass.
      System.out.print(new B().getClass().getName());
    }
  }

  public ExtendsMergedTypeIndirectlyTest(Backend backend, boolean enableVerticalClassMerging) {
    super(backend, enableVerticalClassMerging);
  }

  @Override
  public Class<?> getTestClass() {
    return TestClass.class;
  }

  @Override
  public String getConditionForProguardIfRule() {
    // After class merging, B will no longer extend A (and therefore, TestClass will no longer
    // extend A indirectly), but we should still keep the class Unused in the output.
    return "-if class **$TestClass extends **$A";
  }

  @Override
  public String getExpectedStdout() {
    return B.class.getName();
  }

  public void inspect(CodeInspector inspector) {
    super.inspect(inspector);

    // Verify that TestClass still inherits from B.
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertEquals(B.class.getTypeName(), testClassSubject.getDexClass().superType.toSourceString());
  }
}
