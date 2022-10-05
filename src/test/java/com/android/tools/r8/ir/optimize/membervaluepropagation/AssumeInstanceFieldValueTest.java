// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AssumeInstanceFieldValueTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public AssumeInstanceFieldValueTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(AssumeInstanceFieldValueTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-assumevalues class " + Config.class.getTypeName() + " {",
            "  boolean alwaysTrue return true;",
            "}",
            "-assumenosideeffects class " + Config.class.getTypeName() + " {",
            "  boolean alwaysTrueNoSideEffects return true;",
            "}")
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject configClassSubject = inspector.clazz(Config.class);
    assertThat(configClassSubject, isPresent());

    FieldSubject alwaysTrueFieldSubject =
        configClassSubject.uniqueFieldWithOriginalName("alwaysTrue");
    assertThat(alwaysTrueFieldSubject, isAbsent());

    FieldSubject alwaysTrueNoSideEffectsFieldSubject =
        configClassSubject.uniqueFieldWithOriginalName("alwaysTrueNoSideEffects");
    assertThat(alwaysTrueNoSideEffectsFieldSubject, not(isPresent()));

    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    MethodSubject mainMethodSubject = testClassSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertThat(mainMethodSubject, invokesMethodWithName("getClass"));
  }

  static class TestClass {

    public static void main(String[] args) {
      if (new Config().alwaysTrue) {
        System.out.print("Hello");
      }
      Config nullableConfig = System.currentTimeMillis() >= 0 ? new Config() : null;
      if (nullableConfig.alwaysTrue) {
        System.out.print(" world");
      }
      if (nullableConfig.alwaysTrueNoSideEffects) {
        System.out.println("!");
      }
    }
  }

  static class Config {

    boolean alwaysTrue;
    boolean alwaysTrueNoSideEffects;
  }
}
