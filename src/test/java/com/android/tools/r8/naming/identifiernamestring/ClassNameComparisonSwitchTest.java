// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.identifiernamestring;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassNameComparisonSwitchTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassNameComparisonSwitchTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ClassNameComparisonSwitchTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyClassesHaveBeenMinified)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("A", "B", "C");
  }

  private void verifyClassesHaveBeenMinified(CodeInspector inspector) {
    for (Class<?> clazz : ImmutableList.of(A.class, B.class, C.class)) {
      ClassSubject classSubject = inspector.clazz(clazz);
      assertThat(classSubject, isPresentAndRenamed());
    }
  }

  @Test
  public void testCorrectnessOfNames() {
    assertEquals(A.class.getName(), TestClass.NAME_A);
    assertEquals(B.class.getName(), TestClass.NAME_B);
  }

  static class TestClass {

    private static final String NAME_A =
        "com.android.tools.r8.naming.identifiernamestring.ClassNameComparisonSwitchTest$A";

    private static final String NAME_B =
        "com.android.tools.r8.naming.identifiernamestring.ClassNameComparisonSwitchTest$B";

    public static void main(String[] args) {
      System.out.println(test(A.class));
      System.out.println(test(B.class));
      System.out.println(test(C.class));
    }

    @NeverInline
    static String test(Class<?> clazz) {
      switch (clazz.getName()) {
        case NAME_A:
          return "A";
        case NAME_B:
          return "B";
        default:
          return "C";
      }
    }
  }

  static class A {}

  static class B {}

  static class C {}
}
