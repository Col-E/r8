// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing.enummerging;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.enumunboxing.EnumUnboxingTestBase;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SuperToStringEnumMergingTest extends EnumUnboxingTestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines("> A", "B", "! C", "> - A", "- B", "! - C");

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public SuperToStringEnumMergingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(SuperToStringEnumMergingTest.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(
            inspector -> inspector.assertUnboxed(EnumToString.class, EnumToStringOverride.class))
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .addOptionsModification(opt -> opt.testing.enableEnumUnboxingDebugLogs = true)
        .setMinApi(parameters)
        .allowDiagnosticInfoMessages()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  enum EnumToString {
    A {
      @NeverInline
      @Override
      public String toString() {
        return "> " + super.toString();
      }
    },
    B,
    C {
      @NeverInline
      @Override
      public String toString() {
        return "! " + super.toString();
      }
    };
  }

  enum EnumToStringOverride {
    A {
      @NeverInline
      @Override
      public String toString() {
        return "> " + super.toString();
      }
    },
    B,
    C {
      @NeverInline
      @Override
      public String toString() {
        return "! " + super.toString();
      }
    };

    @NeverInline
    @Override
    public String toString() {
      return "- " + super.toString();
    }
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(EnumToString.A.toString());
      System.out.println(EnumToString.B.toString());
      System.out.println(EnumToString.C.toString());
      System.out.println(EnumToStringOverride.A.toString());
      System.out.println(EnumToStringOverride.B.toString());
      System.out.println(EnumToStringOverride.C.toString());
    }
  }
}
