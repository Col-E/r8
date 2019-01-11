// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.ir.optimize.inliner.testclasses.InliningIntoVisibilityBridgeTestClasses.getClassA;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ir.optimize.inliner.testclasses.InliningIntoVisibilityBridgeTestClasses;
import com.android.tools.r8.ir.optimize.inliner.testclasses.InliningIntoVisibilityBridgeTestClasses.InliningIntoVisibilityBridgeTestClassB;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/120118197. */
@RunWith(Parameterized.class)
public class InliningIntoVisibilityBridgeTest extends TestBase {

  private final boolean neverInline;

  @Parameters(name = "Never inline: {0}")
  public static Boolean[] data() {
    return BooleanUtils.values();
  }

  public InliningIntoVisibilityBridgeTest(boolean neverInline) {
    this.neverInline = neverInline;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Hello world", "Hello world", "Hello world");

    R8TestRunResult result =
        testForR8(Backend.DEX)
            .addInnerClasses(InliningIntoVisibilityBridgeTest.class)
            .addInnerClasses(InliningIntoVisibilityBridgeTestClasses.class)
            .addKeepMainRule(TestClass.class)
            .addKeepRules(
                neverInline
                    ? ("-neverinline class " + getClassA().getTypeName() + " { method(); }")
                    : "")
            .enableMergeAnnotations()
            .enableProguardTestOptions()
            .compile()
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput);

    // Verify that A.method() is only there if there is an explicit -neverinline rule.
    {
      ClassSubject classSubject = result.inspector().clazz(getClassA());
      assertThat(classSubject, isPresent());

      MethodSubject methodSubject = classSubject.uniqueMethodWithName("method");
      assertEquals(neverInline, methodSubject.isPresent());
    }

    // Verify that B.method() is still there, and that B.method() is neither a bridge nor a
    // synthetic method unless there is an explicit -neverinline rule.
    {
      ClassSubject classSubject =
          result.inspector().clazz(InliningIntoVisibilityBridgeTestClassB.class);
      assertThat(classSubject, isPresent());

      MethodSubject methodSubject = classSubject.uniqueMethodWithName("method");
      assertThat(methodSubject, isPresent());
      assertThat(methodSubject, isRenamed());
      assertEquals(neverInline, methodSubject.isBridge());
      assertEquals(neverInline, methodSubject.isSynthetic());
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      InliningIntoVisibilityBridgeTestClassC obj = new InliningIntoVisibilityBridgeTestClassC();

      // Invoke method three times to prevent the synthetic bridge on InliningIntoVisibilityBridge-
      // TestClassB from being inlined.
      obj.method();
      obj.method();
      obj.method();
    }
  }

  static class InliningIntoVisibilityBridgeTestClassC
      extends InliningIntoVisibilityBridgeTestClassB {}
}
