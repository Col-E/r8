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
public class NullCheckEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public NullCheckEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(NullCheckEnumUnboxingTest.class)
        .addKeepMainRule(MainNullTest.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        // MyEnum19 is always unboxed. If minAPI > 19 the unboxer will identify
        // Objects#requiredNonNull usage. For 19 and prior, the backport code should not
        // prohibit the unboxing either.
        .addEnumUnboxingInspector(
            inspector -> inspector.assertUnboxed(MyEnum.class, MyEnum19.class))
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .allowDiagnosticMessages()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), MainNullTest.class)
        .assertSuccess()
        .inspectStdOut(this::assertLines2By2Correct);
  }

  @NeverClassInline
  enum MyEnum {
    A,
    B,
    C
  }

  @NeverClassInline
  enum MyEnum19 {
    A,
    B,
    C
  }

  static class MainNullTest {

    public static void main(String[] args) {
      nullCheckTests();
      nullCheckMessageTests();
    }

    private static void nullCheckTests() {
      nullCheck0Test(MyEnum.A, false);
      nullCheck0Test(MyEnum.B, false);
      nullCheck0Test(null, true);

      nullCheck1Test(MyEnum.A, false);
      nullCheck1Test(MyEnum.B, false);
      nullCheck1Test(null, true);

      nullCheck2Test(MyEnum19.A, false);
      nullCheck2Test(MyEnum19.B, false);
      nullCheck2Test(null, true);
    }

    private static void nullCheckMessageTests() {
      nullCheckMessage0Test(MyEnum.A, "myMessageA", false);
      nullCheckMessage0Test(MyEnum.B, "myMessageB", false);
      nullCheckMessage0Test(null, "myMessageN", true);

      nullCheckMessage1Test(MyEnum19.A, "myMessageA", false);
      nullCheckMessage1Test(MyEnum19.B, "myMessageB", false);
      nullCheckMessage1Test(null, "myMessageN", true);
    }

    private static void nullCheck0Test(MyEnum input, boolean isNull) {
      String result = "pass";
      try {
        nullCheck0(input);
      } catch (NullPointerException ex8) {
        result = "fail";
      }
      System.out.println(result);
      System.out.println(isNull ? "fail" : "pass");
    }

    private static void nullCheck1Test(MyEnum input, boolean isNull) {
      String result = "pass";
      try {
        nullCheck1(input);
      } catch (NullPointerException ex8) {
        result = "fail";
      }
      System.out.println(result);
      System.out.println(isNull ? "fail" : "pass");
    }

    private static void nullCheck2Test(MyEnum19 input, boolean isNull) {
      String result = "pass";
      try {
        nullCheck2(input);
      } catch (NullPointerException ex) {
        result = "fail";
      }
      System.out.println(result);
      System.out.println(isNull ? "fail" : "pass");
    }

    private static void nullCheckMessage0Test(MyEnum input, String message, boolean isNull) {
      String result = "pass";
      try {
        nullCheckMessage0(input, message);
      } catch (NullPointerException ex) {
        result = ex.getMessage();
      }
      System.out.println(result);
      System.out.println(isNull ? message : "pass");
    }

    private static void nullCheckMessage1Test(MyEnum19 input, String message, boolean isNull) {
      String result = "pass";
      try {
        nullCheckMessage1(input, message);
      } catch (NullPointerException ex) {
        result = ex.getMessage();
      }
      System.out.println(result);
      System.out.println(isNull ? message : "pass");
    }

    @NeverInline
    private static void nullCheck0(MyEnum e) {
      if (e == null) {
        throw new NullPointerException();
      }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @NeverInline
    private static void nullCheck1(MyEnum e) {
      e.getClass();
    }

    @NeverInline
    private static void nullCheck2(MyEnum19 e) {
      Objects.requireNonNull(e);
    }

    @NeverInline
    private static void nullCheckMessage0(MyEnum e, String message) {
      if (e == null) {
        throw new NullPointerException(message);
      }
    }

    @NeverInline
    private static void nullCheckMessage1(MyEnum19 e, String message) {
      Objects.requireNonNull(e, message);
    }
  }
}
