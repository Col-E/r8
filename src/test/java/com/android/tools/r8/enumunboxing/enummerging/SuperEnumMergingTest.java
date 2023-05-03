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
public class SuperEnumMergingTest extends EnumUnboxingTestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "a", "top", "A", "A", "A", "> A", "subA", "=", "B", "B", "=", "c", "top", "C", "C", "C",
          "> C", "subB");

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public SuperEnumMergingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(SuperEnumMergingTest.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(EnumWithSuper.class))
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .addOptionsModification(opt -> opt.testing.enableEnumUnboxingDebugLogs = true)
        .setMinApi(parameters)
        .allowDiagnosticInfoMessages()
        .allowUnusedProguardConfigurationRules()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  enum EnumWithSuper {
    A {
      @NeverInline
      @Override
      public void method() {
        System.out.println("a");
        super.top();
        super.method();
        System.out.println(super.name());
        System.out.println(super.toString());
        sub();
      }

      @NeverInline
      public void sub() {
        System.out.println("subA");
      }
    },
    B,
    C {
      @NeverInline
      @Override
      public void method() {
        System.out.println("c");
        super.top();
        super.method();
        System.out.println(super.name());
        System.out.println(super.toString());
        sub();
      }

      @NeverInline
      public void sub() {
        System.out.println("subB");
      }
    };

    @NeverInline
    public void method() {
      System.out.println(super.name());
      System.out.println(super.toString());
    }

    @NeverInline
    @Override
    public String toString() {
      return "> " + name();
    }

    @NeverInline
    public void top() {
      System.out.println("top");
    }
  }

  static class Main {

    public static void main(String[] args) {
      EnumWithSuper.A.method();
      System.out.println("=");
      EnumWithSuper.B.method();
      System.out.println("=");
      EnumWithSuper.C.method();
    }
  }
}
