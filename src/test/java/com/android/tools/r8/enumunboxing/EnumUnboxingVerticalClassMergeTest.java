// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static org.junit.Assert.assertFalse;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EnumUnboxingVerticalClassMergeTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameterized.Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public EnumUnboxingVerticalClassMergeTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    R8TestRunResult run =
        testForR8(parameters.getBackend())
            .addInnerClasses(EnumUnboxingVerticalClassMergeTest.class)
            .addKeepMainRule(Main.class)
            .addKeepRules(enumKeepRules.getKeepRules())
            .enableNeverClassInliningAnnotations()
            .enableInliningAnnotations()
            .noMinification() // For assertions.
            .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
            .allowDiagnosticInfoMessages()
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspect(this::assertVerticalClassMerged)
            .inspectDiagnosticMessages(
                m ->
                    assertEnumIsUnboxed(
                        UnboxableEnum.class,
                        EnumUnboxingVerticalClassMergeTest.class.getSimpleName(),
                        m))
            .run(parameters.getRuntime(), Main.class)
            .assertSuccess();
    assertLines2By2Correct(run.getStdOut());
  }

  private void assertVerticalClassMerged(CodeInspector codeInspector) {
    assertFalse(codeInspector.clazz(Merge1.class).isPresent());
  }

  static class Main {
    public static void main(String[] args) {
      Merge1 merge = new Merge2();
      merge.print();
      System.out.println("print");
      merge.printDefault();
      System.out.println("print default");
      UnboxableEnum.A.enumPrint();
      System.out.println("0");
      UnboxableEnum.B.enumPrint();
      System.out.println("1");
    }

    @NeverInline
    static void test(UnboxableEnum e) {
      System.out.println(e.ordinal());
    }
  }

  static class Merge2 extends Merge1 {
    @Override
    public void print() {
      System.out.println("print");
    }
  }

  abstract static class Merge1 {
    abstract void print();

    void printDefault() {
      System.out.println("print default");
    }
  }

  @NeverClassInline
  enum UnboxableEnum {
    A,
    B,
    C;

    @NeverInline
    void enumPrint() {
      Main.test(this);
    }
  }
}
