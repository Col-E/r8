// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import java.util.List;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EqualsCompareToEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public EqualsCompareToEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<?> success = EnumEqualscompareTo.class;
    testForR8(parameters.getBackend())
        .addInnerClasses(EqualsCompareToEnumUnboxingTest.class)
        .addKeepMainRule(EnumEqualscompareTo.class)
        .addEnumUnboxingInspector(
            inspector -> inspector.assertUnboxed(EnumEqualscompareTo.MyEnum.class))
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addKeepRules(enumKeepRules.getKeepRules())
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), success)
        .assertSuccess()
        .inspectStdOut(this::assertLines2By2Correct);
  }

  static class EnumEqualscompareTo {

    @NeverClassInline
    enum MyEnum {
      A,
      B
    }

    public static void main(String[] args) {
      equalsTest();
      objectsEqualsTest();
      compareToTest();
    }

    @NeverInline
    @SuppressWarnings({"ConstantConditions", "EqualsWithItself", "ResultOfMethodCallIgnored"})
    private static void objectsEqualsTest() {
      System.out.println(performObjectsEquals(MyEnum.A, MyEnum.B));
      System.out.println(false);
      System.out.println(performObjectsEquals(MyEnum.A, MyEnum.A));
      System.out.println(true);
      System.out.println(performObjectsEquals(MyEnum.A, null));
      System.out.println(false);
      System.out.println(performObjectsEquals(null, MyEnum.A));
      System.out.println(false);
      System.out.println(performObjectsEquals(null, null));
      System.out.println(true);
    }

    @NeverInline
    private static boolean performObjectsEquals(MyEnum a, MyEnum b) {
      return Objects.equals(a, b);
    }

    @NeverInline
    @SuppressWarnings({"ConstantConditions", "EqualsWithItself", "ResultOfMethodCallIgnored"})
    private static void equalsTest() {
      System.out.println(MyEnum.A.equals(MyEnum.B));
      System.out.println(false);
      System.out.println(MyEnum.A.equals(MyEnum.A));
      System.out.println(true);
      System.out.println(MyEnum.A.equals(null));
      System.out.println(false);
      try {
        ((MyEnum) null).equals(null);
      } catch (NullPointerException npe) {
        System.out.println("npe " + npe.getMessage());
        System.out.println("npe " + npe.getMessage());
      }
    }

    @NeverInline
    @SuppressWarnings({"ConstantConditions", "EqualsWithItself", "ResultOfMethodCallIgnored"})
    private static void compareToTest() {
      System.out.println(MyEnum.B.compareTo(MyEnum.A) > 0);
      System.out.println(true);
      System.out.println(MyEnum.A.compareTo(MyEnum.B) < 0);
      System.out.println(true);
      System.out.println(MyEnum.A.compareTo(MyEnum.A) == 0);
      System.out.println(true);
      try {
        ((MyEnum) null).equals(null);
      } catch (NullPointerException npe) {
        System.out.println("npe " + npe.getMessage());
        System.out.println("npe " + npe.getMessage());
      }
      try {
        MyEnum.A.compareTo(null);
      } catch (NullPointerException npe) {
        System.out.println("npe " + npe.getMessage());
        System.out.println("npe " + npe.getMessage());
      }
    }
  }
}
