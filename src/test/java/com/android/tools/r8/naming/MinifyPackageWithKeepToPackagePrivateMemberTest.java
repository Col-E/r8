// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MinifyPackageWithKeepToPackagePrivateMemberTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MinifyPackageWithKeepToPackagePrivateMemberTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(
            transformer(A.class).removeInnerClasses().transform(),
            transformer(ReflectiveCallerOfA.class).removeInnerClasses().transform(),
            transformer(Main.class).removeInnerClasses().transform())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!", "Hello World!");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(A.class).removeInnerClasses().transform(),
            transformer(ReflectiveCallerOfA.class).removeInnerClasses().transform(),
            transformer(Main.class).removeInnerClasses().transform())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRulesWithAllowObfuscation(A.class)
        .addKeepRules("-keepclassmembernames class ** { *; }")
        .addKeepRules(
            "-identifiernamestring class "
                + ReflectiveCallerOfA.class.getTypeName()
                + " { java.lang.String className; }")
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!", "Hello World!");
  }

  static class A {

    A() {}

    String foo = "Hello World!";

    void foo() {
      System.out.println("Hello World!");
    }
  }

  public static class ReflectiveCallerOfA {

    private static String className =
        "com.android.tools.r8.naming.MinifyPackageWithKeepToPackagePrivateMemberTest$A";

    @NeverInline
    public static void callA() throws Exception {
      Class<?> aClass = Class.forName(className);
      Object o = aClass.getDeclaredConstructor().newInstance();
      System.out.println(aClass.getDeclaredField("foo").get(o));
      aClass.getDeclaredMethod("foo").invoke(o);
    }
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      ReflectiveCallerOfA.callA();
    }
  }
}
