// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ConsequentRootSetWithSatisfiedDependentItemsTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ConsequentRootSetWithSatisfiedDependentItemsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ConsequentRootSetWithSatisfiedDependentItemsTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-if class " + A.class.getTypeName(),
            "-keepclassmembers class " + A.class.getTypeName() + "{",
            "  <init>();",
            "}")
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccess();
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());
    assertFalse(aClassSubject.getDexProgramClass().isAbstract());
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      Class<?> clazz = System.currentTimeMillis() > 0 ? A.class : null;
      instantiate(clazz);
    }

    static A instantiate(Class<?> clazz) throws Exception {
      return (A) clazz.getDeclaredConstructor().newInstance();
    }
  }

  static class A {

    int x;

    A() {
      this(42);
    }

    A(int x) {
      this.x = x;
    }
  }
}
