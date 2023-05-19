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
public class StringValueOfEnumUnboxingTest extends EnumUnboxingTestBase {
  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public StringValueOfEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<?> classToTest = Main.class;
    testForR8(parameters.getBackend())
        .addProgramClasses(classToTest, MyEnum.class)
        .addKeepMainRule(classToTest)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), classToTest)
        .assertSuccess()
        .inspectStdOut(this::assertLines2By2Correct);
  }

  @NeverClassInline
  enum MyEnum {
    A,
    B,
    C
  }

  static class Main {
    public static void main(String[] args) {
      System.out.println(MyEnum.A.ordinal());
      System.out.println(0);
      stringValueOf();
      objectsToString();
      stringBuilder();
    }

    private static void objectsToString() {
      System.out.println(getStringThroughObjects(MyEnum.A));
      System.out.println("A");
      System.out.println(getStringThroughObjects(null));
      System.out.println("null");
    }

    private static void stringValueOf() {
      System.out.println(getString(MyEnum.A));
      System.out.println("A");
      System.out.println(getString(null));
      System.out.println("null");
    }

    private static void stringBuilder() {
      StringBuilder stringBuilder = new StringBuilder();
      append(stringBuilder, MyEnum.A);
      append(stringBuilder, MyEnum.B);
      append(stringBuilder, null);
      appendTryCatch(stringBuilder, MyEnum.A);
      appendTryCatch(stringBuilder, MyEnum.B);
      appendTryCatch(stringBuilder, null);
      System.out.println(stringBuilder.toString());
      System.out.println("ABnullABnull");

      StringBuffer stringBuffer = new StringBuffer();
      append(stringBuffer, MyEnum.A);
      append(stringBuffer, MyEnum.B);
      append(stringBuffer, null);
      appendTryCatch(stringBuffer, MyEnum.A);
      appendTryCatch(stringBuffer, MyEnum.B);
      appendTryCatch(stringBuffer, null);
      System.out.println(stringBuffer.toString());
      System.out.println("ABnullABnull");
    }

    @NeverInline
    private static StringBuilder append(StringBuilder sb, MyEnum e) {
      return sb.append(e);
    }

    @NeverInline
    private static StringBuffer append(StringBuffer sb, MyEnum e) {
      return sb.append(e);
    }

    @NeverInline
    private static StringBuilder appendTryCatch(StringBuilder sb, MyEnum e) {
      try {
        sb.append(e);
        throwNull();
      } catch (NullPointerException ignored) {
      }
      return sb;
    }

    @NeverInline
    private static StringBuffer appendTryCatch(StringBuffer sb, MyEnum e) {
      try {
        sb.append(e);
        throwNull();
      } catch (NullPointerException ignored) {
      }
      return sb;
    }

    @NeverInline
    private static void throwNull() {
      if (System.currentTimeMillis() > 0) {
        throw new NullPointerException("exception");
      }
    }

    @NeverInline
    private static String getString(MyEnum e) {
      return String.valueOf(e);
    }

    @NeverInline
    private static String getStringThroughObjects(MyEnum e) {
      return Objects.toString(e);
    }
  }
}
