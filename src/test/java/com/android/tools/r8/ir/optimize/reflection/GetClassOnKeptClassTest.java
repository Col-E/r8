// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import java.util.concurrent.Callable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GetClassOnKeptClassTest extends TestBase {

  static final String EXPECTED =
      StringUtils.lines(
          "class " + KeptClass.class.getTypeName(),
          "class " + KeptClass.class.getTypeName(),
          "class " + UnknownClass.class.getTypeName(),
          "class " + UnknownClass.class.getTypeName());

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public GetClassOnKeptClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(KeptClass.class, UnknownClass.class, TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .enableInliningAnnotations()
        .addProgramClasses(KeptClass.class, TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addKeepClassRules(KeptClass.class)
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(buildOnDexRuntime(parameters, UnknownClass.class))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  static class KeptClass implements Callable<Class<?>> {
    @NeverInline
    static Class<?> getClassMethod(KeptClass instance) {
      // Nullable argument. Should not be rewritten to const-class to preserve NPE.
      return instance.getClass();
    }

    @NeverInline
    @Override
    public Class<?> call() {
      // Non-null `this` pointer.
      return getClass();
    }
  }

  static class UnknownClass extends KeptClass {
    // Empty subtype of KeptClass.
  }

  static class TestClass {

    static KeptClass getInstance(int i) throws Exception {
      return i == 0
          ? new KeptClass()
          : (KeptClass)
              Class.forName(TestClass.class.getName().replace("TestClass", "UnknownClass"))
                  .getDeclaredConstructor()
                  .newInstance();
    }

    public static void main(String[] args) throws Exception {
      for (int i = 0; i < 2; i++) {
        KeptClass instance = getInstance(args.length + i);
        System.out.println(instance.call());
        System.out.println(KeptClass.getClassMethod(instance));
      }
    }
  }
}
