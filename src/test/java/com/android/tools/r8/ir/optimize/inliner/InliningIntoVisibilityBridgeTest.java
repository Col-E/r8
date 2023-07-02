// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.ir.optimize.inliner.testclasses.InliningIntoVisibilityBridgeTestClasses.getClassA;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ir.optimize.inliner.testclasses.InliningIntoVisibilityBridgeTestClasses;
import com.android.tools.r8.ir.optimize.inliner.testclasses.InliningIntoVisibilityBridgeTestClasses.InliningIntoVisibilityBridgeTestClassB;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/120118197. */
@RunWith(Parameterized.class)
public class InliningIntoVisibilityBridgeTest extends TestBase {

  private final TestParameters parameters;
  private final boolean neverInline;

  @Parameters(name = "{0}, never inline: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public InliningIntoVisibilityBridgeTest(TestParameters parameters, boolean neverInline) {
    this.parameters = parameters;
    this.neverInline = neverInline;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.times(StringUtils.lines("Hello world"), 6);

    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addInnerClasses(InliningIntoVisibilityBridgeTest.class)
            .addInnerClasses(InliningIntoVisibilityBridgeTestClasses.class)
            .addKeepMainRule(TestClass.class)
            .addInliningAnnotations()
            .applyIf(neverInline, R8TestBuilder::enableInliningAnnotations)
            .enableNoAccessModificationAnnotationsForClasses()
            .enableNoVerticalClassMergingAnnotations()
            .enableProguardTestOptions()
            .setMinApi(parameters)
            .compile()
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(expectedOutput);

    // Verify that A.method() is only there if there is an explicit -neverinline rule.
    {
      ClassSubject classSubject = result.inspector().clazz(getClassA());
      assertThat(classSubject, isPresent());

      MethodSubject methodSubject = classSubject.uniqueMethodWithOriginalName("method");
      assertEquals(neverInline, methodSubject.isPresent());
    }

    // Verify that B.method() is still there, and that B.method() is neither a bridge nor a
    // synthetic method unless there is an explicit -neverinline rule.
    {
      ClassSubject classSubject =
          result.inspector().clazz(InliningIntoVisibilityBridgeTestClassB.class);
      assertThat(classSubject, isPresent());

      MethodSubject methodSubject = classSubject.uniqueMethodWithOriginalName("method");
      if (!neverInline) {
        assertThat(methodSubject, isPresentAndRenamed());
        assertFalse(methodSubject.isBridge());
        assertFalse(methodSubject.isSynthetic());
      }
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      // Invoke method multiple times to prevent the synthetic bridge on
      // InliningIntoVisibilityBridgeTestClassB from being inlined.
      InliningIntoVisibilityBridgeTestClassC.method();
      InliningIntoVisibilityBridgeTestClassC.method();
      InliningIntoVisibilityBridgeTestClassC.method();
      InliningIntoVisibilityBridgeTestClassC.method();
      InliningIntoVisibilityBridgeTestClassC.method();
      InliningIntoVisibilityBridgeTestClassC.method();
    }
  }

  static class InliningIntoVisibilityBridgeTestClassC
      extends InliningIntoVisibilityBridgeTestClassB {}
}
