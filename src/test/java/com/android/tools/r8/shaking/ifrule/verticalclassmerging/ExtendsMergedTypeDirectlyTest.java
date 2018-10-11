// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.verticalclassmerging;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;

public class ExtendsMergedTypeDirectlyTest extends MergedTypeBaseTest {

  static class TestClass extends C {

    public static void main(String[] args) {
      System.out.print("Hello world");
    }
  }

  public ExtendsMergedTypeDirectlyTest(Backend backend, boolean enableVerticalClassMerging) {
    super(backend, enableVerticalClassMerging);
  }

  @Override
  public Class<?> getTestClass() {
    return TestClass.class;
  }

  @Override
  public String getConditionForProguardIfRule() {
    // After class merging, TestClass will no longer extend C, but we should still keep the class
    // Unused in the output.
    return "-if class **$TestClass extends **$C";
  }

  @Override
  public String getExpectedStdout() {
    return "Hello world";
  }

  public void inspect(CodeInspector inspector) {
    super.inspect(inspector);

    if (enableVerticalClassMerging) {
      // Check that TestClass no longer extends C.
      ClassSubject testClassSubject = inspector.clazz(TestClass.class);
      assertThat(testClassSubject, isPresent());
      assertEquals("java.lang.Object", testClassSubject.getDexClass().superType.toSourceString());

      // Check that C is no longer present.
      assertThat(inspector.clazz(C.class), not(isPresent()));
    }
  }
}
