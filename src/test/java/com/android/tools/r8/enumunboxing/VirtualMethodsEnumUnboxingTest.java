// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VirtualMethodsEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final KeepRule enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public VirtualMethodsEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, KeepRule enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<?> classToTest = VirtualMethods.class;
    R8TestRunResult run =
        testForR8(parameters.getBackend())
            .addInnerClasses(VirtualMethodsEnumUnboxingTest.class)
            .addKeepMainRule(classToTest)
            .addKeepRules(enumKeepRules.getKeepRule())
            .enableNeverClassInliningAnnotations()
            .enableInliningAnnotations()
            .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
            .allowDiagnosticInfoMessages()
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspectDiagnosticMessages(
                m -> {
                  assertEnumIsUnboxed(MyEnum.class, classToTest.getSimpleName(), m);
                  assertEnumIsUnboxed(MyEnum2.class, classToTest.getSimpleName(), m);
                })
            .run(parameters.getRuntime(), classToTest)
            .assertSuccess();
    assertLines2By2Correct(run.getStdOut());
  }

  @SuppressWarnings("SameParameterValue")
  @NeverClassInline
  enum MyEnum {
    A,
    B,
    C;

    @NeverInline
    public void print(Object o) {
      System.out.println(o);
    }

    @NeverInline
    public void printEnum(MyEnum e) {
      System.out.println(e.ordinal());
    }

    @NeverInline
    public MyEnum returnEnum(boolean bool) {
      return bool ? MyEnum.A : MyEnum.B;
    }

    @NeverInline
    protected void printProtected() {
      System.out.println("protected");
    }

    @NeverInline
    void printPackagePrivate() {
      System.out.println("package-private");
    }

    @NeverInline
    private void printPrivate() {
      System.out.println("private");
    }

    @NeverInline
    public void callPrivate() {
      System.out.print("call: ");
      printPrivate();
    }
  }

  // Use two enums to test collision.
  enum MyEnum2 {
    A,
    B,
    C;

    @NeverInline
    public void print(Object o) {
      System.out.println("2" + o);
    }

    @NeverInline
    public void printEnum(MyEnum e) {
      System.out.println("2" + e.ordinal());
    }

    @NeverInline
    public MyEnum returnEnum(boolean bool) {
      return bool ? MyEnum.B : MyEnum.C;
    }
  }

  static class VirtualMethods {

    public static void main(String[] args) {
      testCustomMethods();
      testCustomMethods2();
      testNonPublicMethods();
    }

    @NeverInline
    private static void testNonPublicMethods() {
      MyEnum.A.printPrivate();
      System.out.println("private");
      MyEnum.A.printPackagePrivate();
      System.out.println("package-private");
      MyEnum.A.printProtected();
      System.out.println("protected");
      MyEnum.A.callPrivate();
      System.out.println("call: private");
    }

    @NeverInline
    private static void testCustomMethods() {
      MyEnum.A.print("print");
      System.out.println("print");
      MyEnum.A.printEnum(MyEnum.A);
      System.out.println(0);
      System.out.println((MyEnum.A.returnEnum(true).ordinal()));
      System.out.println(0);
    }

    @NeverInline
    private static void testCustomMethods2() {
      MyEnum2.A.print("print");
      System.out.println("2print");
      MyEnum2.A.printEnum(MyEnum.A);
      System.out.println(20);
      System.out.println((MyEnum2.A.returnEnum(true).ordinal()));
      System.out.println(1);
    }
  }
}
