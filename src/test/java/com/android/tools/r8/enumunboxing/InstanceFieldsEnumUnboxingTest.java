// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InstanceFieldsEnumUnboxingTest extends EnumUnboxingTestBase {

  private static final Class<?>[] TESTS = {
    FailureIntField.class,
    FailureBoxedInnerEnumField.class,
    FailureUnboxedEnumField.class,
    FailureTooManyUsedFields.class,
    SuccessUnusedField.class,
    SuccessIntField.class,
    SuccessDoubleField.class,
    SuccessIntFieldOrdinal.class,
    SuccessIntFieldInitializerInit.class,
    SuccessStringField.class,
    SuccessMultiConstructorIntField.class,
    SuccessPrivateIntField.class,
  };

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public InstanceFieldsEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    R8TestCompileResult compile =
        testForR8(parameters.getBackend())
            .addInnerClasses(InstanceFieldsEnumUnboxingTest.class)
            .addKeepMainRules(TESTS)
            .addEnumUnboxingInspector(
                inspector ->
                    inspector
                        .assertUnboxed(
                            SuccessUnusedField.EnumField.class,
                            SuccessIntField.EnumField.class,
                            SuccessDoubleField.EnumField.class,
                            SuccessIntFieldOrdinal.EnumField.class,
                            SuccessIntFieldInitializerInit.EnumField.class,
                            SuccessStringField.EnumField.class,
                            SuccessMultiConstructorIntField.EnumField.class,
                            SuccessPrivateIntField.EnumField.class)
                        .assertNotUnboxed(
                            FailureIntField.EnumField.class,
                            FailureBoxedInnerEnumField.EnumField.class,
                            FailureUnboxedEnumField.EnumField.class,
                            FailureTooManyUsedFields.EnumField.class))
            .addDontObfuscate()
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .addKeepRules(enumKeepRules.getKeepRules())
            .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
            .setMinApi(parameters)
            .compile();
    for (Class<?> main : TESTS) {
      testClass(compile, main);
    }
  }

  private void testClass(R8TestCompileResult compile, Class<?> testClass) throws Exception {
    compile
        .run(parameters.getRuntime(), testClass)
        .assertSuccess()
        .inspectStdOut(this::assertLines2By2Correct);
  }

  static class SuccessUnusedField {

    public static void main(String[] args) {
      System.out.println(getEnumA().ordinal());
      System.out.println(0);
      System.out.println(getEnumB().ordinal());
      System.out.println(1);
    }

    @NeverInline
    static EnumField getEnumA() {
      return System.currentTimeMillis() > 0 ? EnumField.A : EnumField.B;
    }

    @NeverInline
    static EnumField getEnumB() {
      return System.currentTimeMillis() > 0 ? EnumField.B : EnumField.A;
    }

    @NeverClassInline
    enum EnumField {
      A(10),
      B(20);

      int field;

      EnumField(int i) {
        this.field = i;
      }
    }
  }

  static class SuccessIntField {

    public static void main(String[] args) {
      System.out.println(getEnumA().field);
      System.out.println(10);
      System.out.println(getEnumB().field);
      System.out.println(20);
    }

    @NeverInline
    static EnumField getEnumA() {
      return System.currentTimeMillis() > 0 ? EnumField.A : EnumField.B;
    }

    @NeverInline
    static EnumField getEnumB() {
      return System.currentTimeMillis() > 0 ? EnumField.B : EnumField.A;
    }

    @NeverClassInline
    enum EnumField {
      A(10),
      B(20);

      int field;

      EnumField(int i) {
        this.field = i;
      }
    }
  }

  static class SuccessPrivateIntField {

    public static void main(String[] args) {
      System.out.println(getEnumA().field);
      System.out.println(10);
      System.out.println(getEnumB().field);
      System.out.println(20);
    }

    @NeverInline
    static EnumField getEnumA() {
      return System.currentTimeMillis() > 0 ? EnumField.A : EnumField.B;
    }

    @NeverInline
    static EnumField getEnumB() {
      return System.currentTimeMillis() > 0 ? EnumField.B : EnumField.A;
    }

    @NeverClassInline
    enum EnumField {
      A(10),
      B(20);

      private int field;

      EnumField(int i) {
        this.field = i;
      }
    }
  }

  static class SuccessDoubleField {

    public static void main(String[] args) {
      System.out.println(getEnumA().field);
      System.out.println(10.0);
      System.out.println(getEnumB().field);
      System.out.println(20.0);
    }

    @NeverInline
    static EnumField getEnumA() {
      return System.currentTimeMillis() > 0 ? EnumField.A : EnumField.B;
    }

    @NeverInline
    static EnumField getEnumB() {
      return System.currentTimeMillis() > 0 ? EnumField.B : EnumField.A;
    }

    @NeverClassInline
    enum EnumField {
      A(10.0),
      B(20.0);

      double field;

      EnumField(double d) {
        this.field = d;
      }
    }
  }

  static class SuccessIntFieldInitializerInit {

    public static void main(String[] args) {
      System.out.println(getEnumA().field);
      System.out.println(10);
      System.out.println(getEnumB().field);
      System.out.println(10);
    }

    @NeverInline
    static EnumField getEnumA() {
      return System.currentTimeMillis() > 0 ? EnumField.A : EnumField.B;
    }

    @NeverInline
    static EnumField getEnumB() {
      return System.currentTimeMillis() > 0 ? EnumField.B : EnumField.A;
    }

    @NeverClassInline
    enum EnumField {
      A,
      B,
      C;

      int field;

      EnumField() {
        this.field = 10;
      }
    }
  }

  // This class test an optimization where the ordinal is re-used instead of the int field.
  static class SuccessIntFieldOrdinal {

    public static void main(String[] args) {
      System.out.println(getEnumA().field);
      System.out.println(0);
      System.out.println(getEnumB().field);
      System.out.println(1);
    }

    @NeverInline
    static EnumField getEnumA() {
      return System.currentTimeMillis() > 0 ? EnumField.A : EnumField.B;
    }

    @NeverInline
    static EnumField getEnumB() {
      return System.currentTimeMillis() > 0 ? EnumField.B : EnumField.A;
    }

    @NeverClassInline
    enum EnumField {
      A(0),
      B(1),
      C(2);

      int field;

      EnumField(int i) {
        this.field = i;
      }
    }
  }

  static class FailureIntField {

    public static void main(String[] args) {
      System.out.println(getEnumA().field);
      System.out.println(30);
      System.out.println(getEnumB().field);
      System.out.println(60);
    }

    @NeverInline
    static EnumField getEnumA() {
      return System.currentTimeMillis() > 0 ? EnumField.A : EnumField.B;
    }

    @NeverInline
    static EnumField getEnumB() {
      return System.currentTimeMillis() > 0 ? EnumField.B : EnumField.A;
    }

    @NeverClassInline
    enum EnumField {
      A(getRandom(10)),
      B(getRandom(20)),
      C(getRandom(30));

      @NeverInline
      static int getRandom(int i) {
        return i * (System.currentTimeMillis() > 0 ? 3 : -3);
      }

      int field;

      EnumField(int i) {
        this.field = i;
      }
    }
  }

  static class FailureTooManyUsedFields {

    public static void main(String[] args) {
      System.out.println(getEnumA().field0);
      System.out.println(0);
      System.out.println(getEnumA().field1);
      System.out.println(9);
      System.out.println(getEnumA().field2);
      System.out.println(8);
      System.out.println(getEnumA().field3);
      System.out.println(7);
      System.out.println(getEnumA().field4);
      System.out.println(6);
      System.out.println(getEnumA().field5);
      System.out.println(5);
      System.out.println(getEnumA().field6);
      System.out.println(4);
      System.out.println(getEnumA().field7);
      System.out.println(3);
      System.out.println(getEnumA().field8);
      System.out.println(2);
      System.out.println(getEnumA().field9);
      System.out.println(1);
    }

    @NeverInline
    static EnumField getEnumA() {
      return System.currentTimeMillis() > 0 ? EnumField.A : EnumField.B;
    }

    @NeverClassInline
    enum EnumField {
      A(1, 2, 3, 4, 5, 6, 7, 8, 9, 0),
      B(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

      int field0;
      int field1;
      int field2;
      int field3;
      int field4;
      int field5;
      int field6;
      int field7;
      int field8;
      int field9;

      EnumField(int i9, int i8, int i7, int i6, int i5, int i4, int i3, int i2, int i1, int i0) {
        this.field0 = i0;
        this.field1 = i1;
        this.field2 = i2;
        this.field3 = i3;
        this.field4 = i4;
        this.field5 = i5;
        this.field6 = i6;
        this.field7 = i7;
        this.field8 = i8;
        this.field9 = i9;
      }
    }
  }

  static class SuccessMultiConstructorIntField {

    public static void main(String[] args) {
      System.out.println(getEnumA().field0);
      System.out.println(10);
      System.out.println(getEnumA().field1);
      System.out.println(-1);
      System.out.println(getEnumB().field0);
      System.out.println(20);
      System.out.println(getEnumB().field1);
      System.out.println(30);
    }

    @NeverInline
    static EnumField getEnumA() {
      return System.currentTimeMillis() > 0 ? EnumField.A : EnumField.B;
    }

    @NeverInline
    static EnumField getEnumB() {
      return System.currentTimeMillis() > 0 ? EnumField.B : EnumField.A;
    }

    @NeverClassInline
    enum EnumField {
      A(10),
      B(20, 30);

      int field0;
      int field1;

      EnumField(int i0) {
        this(i0, -1);
      }

      EnumField(int i0, int i1) {
        this.field0 = i0;
        this.field1 = i1;
      }
    }
  }

  static class SuccessStringField {

    public static void main(String[] args) {
      System.out.println(getEnumA().field);
      System.out.println("AA");
      System.out.println(getEnumB().field);
      System.out.println("BB");
    }

    @NeverInline
    static EnumField getEnumA() {
      return System.currentTimeMillis() > 0 ? EnumField.A : EnumField.B;
    }

    @NeverInline
    static EnumField getEnumB() {
      return System.currentTimeMillis() > 0 ? EnumField.B : EnumField.A;
    }

    @NeverClassInline
    enum EnumField {
      A("AA"),
      B("BB"),
      C("CC");

      String field;

      EnumField(String s) {
        this.field = s;
      }
    }
  }

  static class FailureBoxedInnerEnumField {

    public static void main(String[] args) {
      System.out.println(getEnumA().field);
      System.out.println("X");
      System.out.println(getEnumB().field);
      System.out.println("Y");
    }

    @NeverInline
    static EnumField getEnumA() {
      return System.currentTimeMillis() > 0 ? EnumField.A : EnumField.B;
    }

    @NeverInline
    static EnumField getEnumB() {
      return System.currentTimeMillis() > 0 ? EnumField.B : EnumField.A;
    }

    @NeverClassInline
    enum InnerEnum {
      X,
      Y,
      Z;
    }

    @NeverClassInline
    enum EnumField {
      A(InnerEnum.X),
      B(InnerEnum.Y),
      C(InnerEnum.Z);

      InnerEnum field;

      EnumField(InnerEnum s) {
        this.field = s;
      }
    }
  }

  static class FailureUnboxedEnumField {

    public static void main(String[] args) {
      System.out.println(getEnumA().field.ordinal());
      System.out.println(0);
      System.out.println(getEnumB().field.ordinal());
      System.out.println(1);
    }

    @NeverInline
    static EnumField getEnumA() {
      return System.currentTimeMillis() > 0 ? EnumField.A : EnumField.B;
    }

    @NeverInline
    static EnumField getEnumB() {
      return System.currentTimeMillis() > 0 ? EnumField.B : EnumField.A;
    }

    @NeverClassInline
    enum InnerEnum {
      X,
      Y,
      Z;
    }

    @NeverClassInline
    enum EnumField {
      A(InnerEnum.X),
      B(InnerEnum.Y),
      C(InnerEnum.Z);

      InnerEnum field;

      EnumField(InnerEnum s) {
        this.field = s;
      }
    }
  }
}
