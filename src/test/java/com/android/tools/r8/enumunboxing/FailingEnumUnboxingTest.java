// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.enumunboxing.FailingEnumUnboxingTest.EnumInstanceFieldMain.EnumInstanceField;
import com.android.tools.r8.enumunboxing.FailingEnumUnboxingTest.EnumStaticFieldMain.EnumStaticField;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FailingEnumUnboxingTest extends EnumUnboxingTestBase {

  private static final Class<?>[] FAILURES = {
    EnumStaticField.class,
    EnumInstanceField.class,
  };

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final KeepRule enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public FailingEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, KeepRule enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxingFailure() throws Exception {
    R8FullTestBuilder r8FullTestBuilder =
        testForR8(parameters.getBackend()).addInnerClasses(FailingEnumUnboxingTest.class);
    for (Class<?> failure : FAILURES) {
      r8FullTestBuilder.addKeepMainRule(failure.getEnclosingClass());
    }
    R8TestCompileResult compile =
        r8FullTestBuilder
            .enableNeverClassInliningAnnotations()
            .addKeepRules(enumKeepRules.getKeepRule())
            .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
            .allowDiagnosticInfoMessages()
            .setMinApi(parameters.getApiLevel())
            .compile();
    for (Class<?> failure : FAILURES) {
      R8TestRunResult run =
          compile
              .inspectDiagnosticMessages(
                  m -> assertEnumIsBoxed(failure, failure.getSimpleName(), m))
              .run(parameters.getRuntime(), failure.getEnclosingClass())
              .assertSuccess();
      assertLines2By2Correct(run.getStdOut());
    }
  }

  static class EnumStaticFieldMain {

    public static void main(String[] args) {
      System.out.println(EnumStaticField.A.ordinal());
      System.out.println(0);
      System.out.println(EnumStaticField.X.ordinal());
      System.out.println(0);
    }

    @NeverClassInline
    enum EnumStaticField {
      A,
      B,
      C;
      static EnumStaticField X = A;
    }
  }

  static class EnumInstanceFieldMain {

    @NeverClassInline
    enum EnumInstanceField {
      A(10),
      B(20),
      C(30);
      private int a;

      EnumInstanceField(int i) {
        this.a = i;
      }
    }

    public static void main(String[] args) {
      System.out.println(EnumInstanceField.A.ordinal());
      System.out.println(0);
      System.out.println(EnumInstanceField.A.a);
      System.out.println(10);
    }
  }
}
