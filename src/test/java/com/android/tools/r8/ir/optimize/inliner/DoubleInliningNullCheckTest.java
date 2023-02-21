// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DoubleInliningNullCheckTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public DoubleInliningNullCheckTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(DoubleInliningNullCheckTest.class)
        .addKeepMainRule(TestClass.class)
        .addDontObfuscate()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("true")
        .inspect(
            codeInspector -> {
              ClassSubject main = codeInspector.clazz(TestClass.class);
              assertThat(main, isPresent());
              MethodSubject mainMethod = main.mainMethod();
              assertThat(mainMethod, isPresent());
              assertEquals(0, countCall(mainMethod, "checkParameterIsNotNull"));
            });
  }

  static class TestClass {
    public static void main(String... args) {
      String[] array = new String[] { "1", "2", "3" };
      System.out.println(ArrayUtil.contains(array, "3"));
    }
  }

  static class ArrayUtil {
    public static void throwParameterIsNull(String paramName) {
      throw new NullPointerException(paramName);
    }

    // Has double callers, indexOf and contains
    public static void checkParameterIsNotNull(Object param, String paramName) {
      if (param == null) {
        throwParameterIsNull(paramName);
      }
    }

    // Has single caller, contains
    public static int indexOf(Object[] array, Object subject) {
      checkParameterIsNotNull(array, "array");
      for (int i = 0; i < array.length; i++) {
        if (array[i].equals(subject)) {
          return i;
        }
      }
      return -1;
    }

    // Has single caller, TestClass#main
    public static boolean contains(Object[] array, Object subject) {
      checkParameterIsNotNull(array, "array");
      return indexOf(array, subject) != -1;
    }
  }
}
