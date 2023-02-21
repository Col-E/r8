// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NoLongerSyntheticConstructorTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public NoLongerSyntheticConstructorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, B.class)
        .addProgramClassFileData(
            transformer(A.class)
                .setAccessFlags(A.class.getDeclaredConstructor(), AccessFlags::setSynthetic)
                .transform())
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-if class " + A.class.getTypeName() + " {",
            "  synthetic <init>();",
            "}",
            "-keep class " + B.class.getTypeName())
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(B.class), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new A());
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

  static class B {}
}
