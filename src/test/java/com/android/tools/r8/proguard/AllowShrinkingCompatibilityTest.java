// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.proguard;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AllowShrinkingCompatibilityTest extends TestBase {

  private final boolean allowOptimization;
  private final TestParameters parameters;
  private final ProguardVersion proguardVersion;

  @Parameters(name = "{1}, {2}, allow optimization: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters().withCfRuntimes().build(),
        ProguardVersion.values());
  }

  public AllowShrinkingCompatibilityTest(
      boolean allowOptimization, TestParameters parameters, ProguardVersion proguardVersion) {
    this.allowOptimization = allowOptimization;
    this.parameters = parameters;
    this.proguardVersion = proguardVersion;
  }

  @Test
  public void test() throws Exception {
    testForProguard(proguardVersion)
        .addProgramClasses(TestClass.class, Companion.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-keep,allowshrinking"
                + (allowOptimization ? ",allowoptimization" : "")
                + " class "
                + Companion.class.getTypeName()
                + " { <methods>; }")
        .addDontWarn(getClass())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject testClassSubject = inspector.clazz(TestClass.class);
              assertThat(testClassSubject, isPresent());

              ClassSubject companionClassSubject = inspector.clazz(Companion.class);
              assertThat(companionClassSubject, notIf(isPresent(), allowOptimization));

              MethodSubject mainMethodSubject = testClassSubject.mainMethod();
              MethodSubject getMethodSubject = companionClassSubject.uniqueMethodWithName("get");

              if (allowOptimization) {
                assertTrue(
                    testClassSubject
                        .mainMethod()
                        .streamInstructions()
                        .allMatch(InstructionSubject::isReturnVoid));
              } else {
                assertThat(mainMethodSubject, invokesMethod(getMethodSubject));
                assertThat(getMethodSubject, isPresent());
              }
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithEmptyOutput();
  }

  static class TestClass {

    public static void main(String[] args) {
      if (Companion.get() != 42) {
        System.out.println("Hello world!");
      }
    }
  }

  static class Companion {

    static int get() {
      return 42;
    }
  }
}
