// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageWithInvokeDynamicTest extends RepackageTestBase {

  public RepackageWithInvokeDynamicTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .apply(this::configureRepackaging)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {}

  public static class TestClass {

    public static void main(String[] args) {
      I greeterFactory = getGreeterFactory("Hello world!");
      greeterFactory.create().greet();
    }

    @NeverInline
    static I getGreeterFactory(String greeting) {
      return () -> new A(greeting);
    }
  }

  public interface I {

    A create();
  }

  public static class A {

    private final String greeting;

    public A(String greeting) {
      this.greeting = greeting;
    }

    @NeverInline
    public void greet() {
      System.out.println(greeting);
    }
  }
}
