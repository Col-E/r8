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
public class SingletonFieldValuePropagationEnumTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SingletonFieldValuePropagationEnumTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(SingletonFieldValuePropagationEnumTest.class)
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
      System.out.println((char) Characters.A.get());
      System.out.println((char) Characters.B.get());
      System.out.println((char) Characters.C.get());
    }
  }

  enum Characters {
    A(65),
    B(66),
    C(67);

    int value;

    Characters(int value) {
      this.value = value;
    }

    int get() {
      return value;
    }
  }
}
