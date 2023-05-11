// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation.fields.singleton;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SingletonFieldValuePropagationEnumWithSubclassTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SingletonFieldValuePropagationEnumWithSubclassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(SingletonFieldValuePropagationEnumWithSubclassTest.class)
        .addKeepMainRule(TestClass.class)
        .addEnumUnboxingInspector(
            inspector ->
                inspector.assertUnboxedIf(
                    parameters.canInitNewInstanceUsingSuperclassConstructor(), Characters.class))
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("A", "B", "C");
  }

  private void inspect(CodeInspector inspector) {
    if (parameters.canInitNewInstanceUsingSuperclassConstructor()) {
      assertThat(inspector.clazz(Characters.class), isAbsent());
    } else {
      ClassSubject charactersClassSubject = inspector.clazz(Characters.class);
      assertThat(charactersClassSubject, isPresent());
      // TODO(b/150368955): Field value propagation should cause Characters.value to become dead.
      assertEquals(1, charactersClassSubject.allInstanceFields().size());
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println((char) Characters.A.get());
      System.out.println((char) Characters.B.get());
      System.out.println((char) Characters.C.get());
    }
  }

  enum Characters {
    A(65) {
      @Override
      int get() {
        return value;
      }
    },
    B(66) {
      @Override
      int get() {
        return value;
      }
    },
    C(67) {
      @Override
      int get() {
        return value;
      }
    };

    int value;

    Characters(int value) {
      this.value = value;
    }

    int get() {
      throw new RuntimeException();
    }
  }
}
