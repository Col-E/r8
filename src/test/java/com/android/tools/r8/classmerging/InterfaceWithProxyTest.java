// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import java.lang.reflect.Proxy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InterfaceWithProxyTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public InterfaceWithProxyTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Hello world!");
    testForR8(parameters.getBackend())
        .addInnerClasses(InterfaceWithProxyTest.class)
        .addKeepMainRule(TestClass.class)
        .enableClassInliningAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  static class TestClass {

    public static void main(String[] args) {
      I obj =
          (I)
              Proxy.newProxyInstance(
                  TestClass.class.getClassLoader(),
                  new Class<?>[] {I.class},
                  (proxy, method, args1) -> {
                    System.out.print("Hello");
                    return null;
                  });
      obj.greet();
      new A().greet();
    }
  }

  interface I {

    void greet();
  }

  @NeverClassInline
  static class A implements I {

    @NeverInline
    @Override
    public void greet() {
      System.out.println(" world!");
    }
  }
}
