// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
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
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public VirtualMethodsEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<?> classToTest = VirtualMethods.class;
    R8TestCompileResult compile =
        testForR8(parameters.getBackend())
            .addInnerClasses(VirtualMethodsEnumUnboxingTest.class)
            .addKeepMainRule(classToTest)
            .addKeepMainRule(VirtualMethodsFail.class)
            .addKeepRules(enumKeepRules.getKeepRules())
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
                  assertEnumIsUnboxed(MyEnumWithCollisions.class, classToTest.getSimpleName(), m);
                  assertEnumIsUnboxed(
                      MyEnumWithPackagePrivateCall.class, classToTest.getSimpleName(), m);
                });
    R8TestRunResult run = compile.run(parameters.getRuntime(), classToTest).assertSuccess();
    assertLines2By2Correct(run.getStdOut());
    // TODO(b/160854837): This test should actually be successful.
    compile
        .run(parameters.getRuntime(), VirtualMethodsFail.class)
        .assertFailureWithErrorThatMatches(containsString("IllegalAccessError"));
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
  @NeverClassInline
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

  @NeverClassInline
  enum MyEnumWithCollisions {
    A,
    B,
    C;

    @NeverInline
    public int get() {
      return get(this);
    }

    @NeverInline
    public static int get(MyEnumWithCollisions e) {
      switch (e) {
        case A:
          return 5;
        case B:
          return 2;
        case C:
          return 1;
      }
      return -1;
    }
  }

  @NeverClassInline
  static class PackagePrivateClass {
    @NeverInline
    static void print() {
      System.out.println("print");
    }
  }

  @NeverClassInline
  enum MyEnumWithPackagePrivateCall {
    A,
    B,
    C;

    @NeverInline
    public static void callPackagePrivate() {
      PackagePrivateClass.print();
    }
  }

  static class VirtualMethodsFail {
    public static void main(String[] args) {
      testCollisions();
      testPackagePrivate();
    }

    @NeverInline
    private static void testPackagePrivate() {
      System.out.println(MyEnumWithPackagePrivateCall.A.ordinal());
      System.out.println(0);
      MyEnumWithPackagePrivateCall.callPackagePrivate();
      System.out.println("print");
    }

    @NeverInline
    private static void testCollisions() {
      System.out.println(MyEnumWithCollisions.A.get());
      System.out.println(5);
      System.out.println(MyEnumWithCollisions.B.get());
      System.out.println(2);
      System.out.println(MyEnumWithCollisions.C.get());
      System.out.println(1);
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
