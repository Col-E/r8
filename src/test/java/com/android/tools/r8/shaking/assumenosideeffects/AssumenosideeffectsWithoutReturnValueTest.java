// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.assumenosideeffects;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AssumenosideeffectsWithoutReturnValueTest extends TestBase {
  private static final Class<?> MAIN = TestClass.class;

  enum TestConfig {
    RULE_WITH_RETURN_VALUE,
    SEPARATE_RULES;

    public String getKeepRules() {
      switch (this) {
        case RULE_WITH_RETURN_VALUE:
          return StringUtils.lines(
              "-assumenosideeffects class ** {",
              "  *** debug(...) return false;",
              "}"
          );
        case SEPARATE_RULES:
          return StringUtils.lines(
              "-assumenosideeffects class ** {",
              "  *** debug(...);",
              "}",
              "-assumevalues class ** {",
              "  *** debug(...) return false;",
              "}"
          );
        default:
          throw new Unreachable();
      }
    }
  }

  private final TestParameters parameters;
  private final TestConfig config;

  @Parameterized.Parameters(name = "{0} {1}")
  public static Collection<Object[]> data() {
    return buildParameters(getTestParameters().withAllRuntimes().build(), TestConfig.values());
  }

  public AssumenosideeffectsWithoutReturnValueTest(
      TestParameters parameters, TestConfig config) {
    this.parameters = parameters;
    this.config = config;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(AssumenosideeffectsWithoutReturnValueTest.class)
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules(config.getKeepRules())
        .addDontObfuscate()
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(this::verifyDebugMethodIsRemoved)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("The end");
  }

  private void verifyDebugMethodIsRemoved(CodeInspector inspector) {
    ClassSubject main = inspector.clazz(MAIN);
    assertThat(main, isPresent());
    MethodSubject debug = main.uniqueMethodWithOriginalName("debug");
    assertThat(debug, not(isPresent()));
  }

  static class TestClass {
    @NeverInline
    boolean debug() {
      return System.currentTimeMillis() > 0;
    }

    public static void main(String... args) {
      TestClass instance = new TestClass();
      if (instance.debug()) {
        System.out.println("message");
      }
      System.out.println("The end");
    }
  }
}
