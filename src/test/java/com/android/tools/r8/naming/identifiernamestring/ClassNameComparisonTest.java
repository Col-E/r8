// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.identifiernamestring;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassNameComparisonTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassNameComparisonTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ClassNameComparisonTest.class)
        .addKeepMainRule(TestClass.class)
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello!", "Hello " + B.class.getName() + "!");
  }

  @Test
  public void testCorrectnessOfNames() {
    assertEquals(A.class.getName(), TestClass.NAME_A);
    assertEquals(B.class.getName(), TestClass.NAME_B);
  }

  static class TestClass {

    private static final String NAME_A =
        "com.android.tools.r8.naming.identifiernamestring.ClassNameComparisonTest$A";

    private static final String NAME_B =
        "com.android.tools.r8.naming.identifiernamestring.ClassNameComparisonTest$B";

    public static void main(String[] args) {
      if (A.class.getName().equals(NAME_A)) {
        System.out.println("Hello!");
      }
      String name = NAME_B;
      if (B.class.getName().equals(name)) {
        System.out.println("Hello " + name + "!");
      }
    }
  }

  @NoHorizontalClassMerging
  static class A {}

  @NoHorizontalClassMerging
  static class B {}
}
