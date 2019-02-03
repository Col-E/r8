// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.desugar;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;

@NeverMerge
interface I {
  static void foo() {
    System.out.println("static::foo");
  }

  default void bar() {
    foo();
    System.out.println("default::bar");
  }
}

class C implements I {
}

class KeepRuleWarningTestRunner {
  public static void main(String[] args) {
    C obj = new C();
    obj.bar();
  }
}

public class KeepRuleWarningTest extends TestBase {
  private static final Class<?> MAIN = KeepRuleWarningTestRunner.class;
  private static final String EXPECTED_OUTPUT = StringUtils.lines("static::foo", "default::bar");

  @Test
  public void test_allMethods() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(I.class, C.class, MAIN)
        .setMinApi(AndroidApiLevel.L)
        .enableMergeAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules("-keep interface **.I { <methods>; }")
        .compile()
        .inspectDiagnosticMessages(TestDiagnosticMessages::assertNoMessages)
        .run(MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void test_asterisk() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(I.class, C.class, MAIN)
        .setMinApi(AndroidApiLevel.L)
        .enableMergeAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules("-keep interface **.I { *(); }")
        .compile()
        .inspectDiagnosticMessages(TestDiagnosticMessages::assertNoMessages)
        .run(MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void test_stillNotSpecific() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(I.class, C.class, MAIN)
        .setMinApi(AndroidApiLevel.L)
        .enableMergeAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules("-keep interface **.I { *** f*(); }")
        .compile()
        .inspectDiagnosticMessages(TestDiagnosticMessages::assertNoMessages)
        .run(MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void test_specific() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(I.class, C.class, MAIN)
        .setMinApi(AndroidApiLevel.L)
        .enableMergeAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules("-keep interface **.I { static void foo(); }")
        .compile()
        .inspectDiagnosticMessages(m -> {
          m.assertWarningsCount(1)
              .assertWarningMessageThatMatches(containsString("static void foo()"))
              .assertWarningMessageThatMatches(containsString("is ignored"))
              .assertWarningMessageThatMatches(containsString("will be desugared"));
        })
        .run(MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

}