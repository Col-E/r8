// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
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
    testForR8(parameters.getBackend())
        .addInnerClasses(VirtualMethodsEnumUnboxingTest.class)
        .addKeepMainRule(classToTest)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(
            inspector ->
                inspector.assertUnboxed(
                    MyEnum.class,
                    MyEnum2.class,
                    MyEnumWithCollisions.class,
                    MyEnumWithPackagePrivateCall.class,
                    MyEnumWithProtectedCall.class,
                    MyEnumWithPackagePrivateFieldAccess.class,
                    MyEnumWithPackagePrivateAndPrivateCall.class))
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), classToTest)
        .assertSuccess()
        .inspectStdOut(this::assertLines2By2Correct);
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
  static class PackagePrivateClassWithPublicMembers {
    @NeverInline
    public static void print() {
      System.out.println("print1");
    }

    public static int item = System.currentTimeMillis() > 0 ? 42 : 41;
  }

  @NeverClassInline
  public static class PublicClassWithPackagePrivateMembers {
    @NeverInline
    static void print() {
      System.out.println("print2");
    }

    static int item = System.currentTimeMillis() > 0 ? 4 : 1;
  }

  @NeverClassInline
  public static class PublicClassWithProtectedMembers {
    @NeverInline
    protected static void print() {
      System.out.println("print3");
    }
  }

  @NeverClassInline
  enum MyEnumWithPackagePrivateCall {
    A,
    B,
    C;

    @NeverInline
    public static void callPackagePrivate() {
      PackagePrivateClassWithPublicMembers.print();
      System.out.println("print1");
      PublicClassWithPackagePrivateMembers.print();
      System.out.println("print2");
    }
  }

  @NeverClassInline
  enum MyEnumWithPackagePrivateAndPrivateCall {
    A,
    B,
    C;

    @NeverInline
    public static void privateMethod() {
      System.out.println("private");
    }

    @NeverInline
    public static void callPackagePrivateAndPrivate() {
      // This method has "SAME_CLASS" as compilation state, yet,
      // it also has a package-private call.
      privateMethod();
      System.out.println("private");
      PackagePrivateClassWithPublicMembers.print();
      System.out.println("print1");
      PublicClassWithPackagePrivateMembers.print();
      System.out.println("print2");
    }
  }

  @NeverClassInline
  enum MyEnumWithProtectedCall {
    A,
    B,
    C;

    @NeverInline
    public static void callProtected() {
      PublicClassWithProtectedMembers.print();
      System.out.println("print3");
    }
  }

  @NeverClassInline
  enum MyEnumWithPackagePrivateFieldAccess {
    A,
    B,
    C;

    @NeverInline
    public static void accessPackagePrivate() {
      System.out.println(PackagePrivateClassWithPublicMembers.item);
      System.out.println("42");
      System.out.println(PublicClassWithPackagePrivateMembers.item);
      System.out.println("4");
    }
  }

  static class VirtualMethods {
    public static void main(String[] args) {
      testCustomMethods();
      testCustomMethods2();
      testNonPublicMethods();
      testCollisions();
      testPackagePrivateMethod();
      testProtectedAccess();
      testPackagePrivateAndPrivateMethod();
      testPackagePrivateAccess();
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

    @NeverInline
    private static void testPackagePrivateMethod() {
      System.out.println(MyEnumWithPackagePrivateCall.A.ordinal());
      System.out.println(0);
      MyEnumWithPackagePrivateCall.callPackagePrivate();
    }

    @NeverInline
    private static void testPackagePrivateAndPrivateMethod() {
      System.out.println(MyEnumWithPackagePrivateAndPrivateCall.A.ordinal());
      System.out.println(0);
      MyEnumWithPackagePrivateAndPrivateCall.callPackagePrivateAndPrivate();
    }

    @NeverInline
    private static void testPackagePrivateAccess() {
      System.out.println(MyEnumWithPackagePrivateFieldAccess.A.ordinal());
      System.out.println(0);
      MyEnumWithPackagePrivateFieldAccess.accessPackagePrivate();
    }

    @NeverInline
    private static void testProtectedAccess() {
      System.out.println(MyEnumWithProtectedCall.A.ordinal());
      System.out.println(0);
      MyEnumWithProtectedCall.callProtected();
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
}
