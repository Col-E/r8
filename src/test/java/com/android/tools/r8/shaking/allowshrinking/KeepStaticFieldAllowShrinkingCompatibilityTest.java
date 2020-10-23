// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.allowshrinking;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.accessesField;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepStaticFieldAllowShrinkingCompatibilityTest extends TestBase {

  private final boolean allowOptimization;
  private final TestParameters parameters;
  private final Shrinker shrinker;

  @Parameters(name = "{1}, {2}, allow optimization: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters().withCfRuntimes().build(),
        ImmutableList.of(Shrinker.R8, Shrinker.PG));
  }

  public KeepStaticFieldAllowShrinkingCompatibilityTest(
      boolean allowOptimization, TestParameters parameters, Shrinker shrinker) {
    this.allowOptimization = allowOptimization;
    this.parameters = parameters;
    this.shrinker = shrinker;
  }

  @Test
  public void test() throws Exception {
    if (shrinker.isPG()) {
      run(testForProguard(shrinker.getProguardVersion()).addDontWarn(getClass()));
    } else {
      run(testForR8(parameters.getBackend()));
    }
  }

  private <T extends TestShrinkerBuilder<?, ?, ?, ?, T>> void run(T builder) throws Exception {
    builder
        .addProgramClasses(TestClass.class, Companion.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-keep,allowshrinking"
                + (allowOptimization ? ",allowoptimization" : "")
                + " class "
                + Companion.class.getTypeName()
                + " { <fields>; }")
        .compile()
        .inspect(
            inspector -> {
              ClassSubject testClassSubject = inspector.clazz(TestClass.class);
              assertThat(testClassSubject, isPresent());

              MethodSubject mainMethodSubject = testClassSubject.mainMethod();
              ClassSubject companionClassSubject = inspector.clazz(Companion.class);
              FieldSubject xFieldSubject = companionClassSubject.uniqueFieldWithName("x");

              // PG fails to optimize fields regardless of keep flags.
              if (allowOptimization && shrinker.isR8()) {
                assertThat(companionClassSubject, not(isPresent()));
                assertTrue(
                    testClassSubject
                        .mainMethod()
                        .streamInstructions()
                        .allMatch(InstructionSubject::isReturnVoid));
              } else {
                assertThat(companionClassSubject, isPresent());
                assertThat(mainMethodSubject, accessesField(xFieldSubject));
                assertThat(xFieldSubject, isPresent());
              }
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithEmptyOutput();
  }

  static class TestClass {

    public static void main(String[] args) {
      if (Companion.x != 42) {
        System.out.println("Hello world!");
      }
    }
  }

  static class Companion {

    static int x = 42;
  }
}
