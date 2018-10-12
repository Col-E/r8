// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.verticalclassmerging;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;

public class ImplementsMergedTypeDirectlyTest extends MergedTypeBaseTest {

  static class TestClass implements K {

    public static void main(String[] args) {
      System.out.print("Hello world");
    }
  }

  public ImplementsMergedTypeDirectlyTest(Backend backend, boolean enableVerticalClassMerging) {
    super(backend, enableVerticalClassMerging);
  }

  @Override
  public Class<?> getTestClass() {
    return TestClass.class;
  }

  @Override
  public String getConditionForProguardIfRule() {
    // After class merging, TestClass will no longer implement K, but we should still keep the
    // class Unused in the output.
    return "-if class **$TestClass implements **$K";
  }

  @Override
  public String getExpectedStdout() {
    return "Hello world";
  }

  public void inspect(CodeInspector inspector) {
    super.inspect(inspector);

    if (enableVerticalClassMerging) {
      // Check that TestClass no longer implements K.
      ClassSubject testClassSubject = inspector.clazz(TestClass.class);
      assertThat(testClassSubject, isPresent());
      assertTrue(testClassSubject.getDexClass().interfaces.isEmpty());

      // Check that K is no longer present.
      assertThat(inspector.clazz(K.class), not(isPresent()));
    }
  }
}
