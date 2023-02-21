// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation.fields.singleton;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SingletonFieldValuePropagationTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SingletonFieldValuePropagationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(SingletonFieldValuePropagationTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("A", "B", "C");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject charactersClassSubject = inspector.clazz(Characters.class);
    assertThat(charactersClassSubject, not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println((char) Characters.getA().get());
      System.out.println((char) Characters.getB().get());
      System.out.println((char) Characters.getC().get());
    }
  }

  static class Characters {

    static Characters INSTANCE_A = new Characters(65);
    static Characters INSTANCE_B = new Characters(66);
    static Characters INSTANCE_C = new Characters(67);

    int value;

    Characters(int value) {
      this.value = value;
    }

    static Characters getA() {
      return INSTANCE_A;
    }

    static Characters getB() {
      return INSTANCE_B;
    }

    static Characters getC() {
      return INSTANCE_C;
    }

    int get() {
      return value;
    }
  }
}
