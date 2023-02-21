// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.lang.reflect.Type;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSignaturePrunedInterfacesTest extends TestBase {

  private final TestParameters parameters;
  private final String[] EXPECTED =
      new String[] {
        "interface com.android.tools.r8.graph.genericsignature"
            + ".GenericSignaturePrunedInterfacesTest$J",
        "com.android.tools.r8.graph.genericsignature"
            + ".GenericSignaturePrunedInterfacesTest$J<java.lang.Object>"
      };

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public GenericSignaturePrunedInterfacesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addKeepClassRules(I.class, J.class, A.class)
        .addKeepAttributeSignature()
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public interface I {}

  public interface J<T> {}

  public static class A implements I {}

  public static class B extends A implements I, J<Object> {

    @NeverInline
    public static void foo() {
      for (Type genericInterface : B.class.getInterfaces()) {
        System.out.println(genericInterface);
      }
      for (Type genericInterface : B.class.getGenericInterfaces()) {
        System.out.println(genericInterface);
      }
    }
  }

  public static class Main {

    public static void main(String[] args) {
      B.foo();
    }
  }
}
