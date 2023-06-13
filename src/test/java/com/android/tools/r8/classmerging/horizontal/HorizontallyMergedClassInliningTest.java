// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class HorizontallyMergedClassInliningTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public HorizontallyMergedClassInliningTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertIsCompleteMergeGroup(A.class, B.class, C.class))
        .allowAccessModification()
        .setMinApi(parameters)
        .addOptionsModification(
            options -> {
              // With removal of redundant blocks this heuristic needs to be raised.
              options.classInlinerOptions().classInliningInstructionAllowance = 66;
            })
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class), isAbsent());
              assertThat(inspector.clazz(B.class), isAbsent());
              assertThat(inspector.clazz(C.class), isAbsent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput("Hello world!");
  }

  static class Main {

    public static void main(String[] args) {
      print(new A().build());
      print(new B().build());
      print(new C().build());
    }

    static void print(String[] strings) {
      for (String string : strings) {
        System.out.print(string);
      }
    }
  }

  static class A {

    String[] build() {
      return new String[] {"H", "e", "l", "l"};
    }
  }

  static class B {

    String[] build() {
      return new String[] {"o", " ", "w", "o"};
    }
  }

  static class C {

    String[] build() {
      return new String[] {"r", "l", "d", "!"};
    }
  }
}
