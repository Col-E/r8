// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.verticalclassmerging;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;

public class ImplementsMergedTypeDirectlyTest extends MergedTypeBaseTest {

  static class TestClass implements K {

    public static void main(String[] args) {
      System.out.print("Hello world");
    }
  }

  public ImplementsMergedTypeDirectlyTest(
      TestParameters parameters, boolean enableVerticalClassMerging) {
    super(parameters, enableVerticalClassMerging);
  }

  @Override
  public void configure(R8FullTestBuilder builder) {
    super.configure(builder);

    // If unused interface removal is enabled, the `implements K` clause will be removed prior to
    // vertical class merging (by the tree pruner).
    // TODO(b/135083634): Should handle unused interfaces similar to vertically merged classes.
    builder.addOptionsModification(options -> options.enableUnusedInterfaceRemoval = false);
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
      assertTrue(testClassSubject.getDexProgramClass().interfaces.isEmpty());

      // Check that K is no longer present.
      assertThat(inspector.clazz(K.class), not(isPresent()));
    }
  }
}
