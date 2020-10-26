// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LambdaHasNonSyntheticMethodTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public LambdaHasNonSyntheticMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  interface MyCallable<T> {
    T call() throws Exception;
  }

  static class TestClass {

    private static void assertNotNull(Object o) {
      if (o == null) throw new AssertionError();
    }

    private static void assertSame(Object o1, Object o2) {
      if (o1 != o2) throw new AssertionError();
    }

    private static void assertFalse(boolean value) {
      if (value) throw new AssertionError();
    }

    private static <T> void assertLambdaMethodIsNotSynthetic(T instance, Class<?> iface)
        throws Exception {
      Method ifaceMethod = null;
      for (Method method : iface.getDeclaredMethods()) {
        if (Modifier.isAbstract(method.getModifiers())) {
          ifaceMethod = method;
          break;
        }
      }
      assertNotNull(ifaceMethod);
      Method lambdaMethod =
          instance.getClass().getMethod(ifaceMethod.getName(), ifaceMethod.getParameterTypes());
      assertSame(ifaceMethod.getReturnType(), lambdaMethod.getReturnType());
      assertSame(instance.getClass(), lambdaMethod.getDeclaringClass());
      assertFalse(lambdaMethod.isSynthetic());
      assertFalse(lambdaMethod.isBridge());
    }

    public static void main(String[] args) throws Exception {
      StringBuilder builder = new StringBuilder("Hello, world");
      MyCallable<String> instance = builder::toString;
      assertLambdaMethodIsNotSynthetic(instance, MyCallable.class);
      System.out.println(instance.call());
    }
  }
}
