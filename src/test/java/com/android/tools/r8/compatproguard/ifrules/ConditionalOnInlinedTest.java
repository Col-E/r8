// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compatproguard.ifrules;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.shaking.methods.MethodsTestBase.Shrinker;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConditionalOnInlinedTest extends TestBase {

  static class A {
    void method(String name) throws Exception {
      Class<?> clazz = Class.forName(name);
      Object object = clazz.getDeclaredConstructor().newInstance();
      clazz.getDeclaredMethod("method").invoke(object);
    }
  }

  static class B {
    void method() {
      System.out.println("B::method");
    }
  }

  static class Main {
    public static void main(String[] args) throws Exception {
      new A().method(args[0]);
    }
  }

  private static Class<?> MAIN_CLASS = Main.class;
  private static Collection<Class<?>> CLASSES = ImmutableList.of(MAIN_CLASS, A.class, B.class);

  private static String EXPECTED = StringUtils.lines("B::method");

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        ArrayUtils.withOptionalNone(Shrinker.values()),
        getTestParameters().withCfRuntimes().build());
  }

  @Parameter(0)
  public Optional<Shrinker> optionalShrinker;

  @Parameter(1)
  public TestParameters parameters;

  @Test
  public void testReference() throws Exception {
    assumeFalse(optionalShrinker.isPresent());
    testForJvm(parameters)
        .addProgramClasses(CLASSES)
        .run(parameters.getRuntime(), MAIN_CLASS, B.class.getTypeName())
        .assertSuccessWithOutput(EXPECTED);
  }

  private TestShrinkerBuilder<?, ?, ?, ?, ?> buildShrinker(Shrinker shrinker) {
    TestShrinkerBuilder<?, ?, ?, ?, ?> builder;
    if (shrinker == Shrinker.Proguard) {
      builder = testForProguard(ProguardVersion.V6_0_1).addDontWarn(ConditionalOnInlinedTest.class);
    } else if (shrinker == Shrinker.R8Compat) {
      builder = testForR8Compat(parameters.getBackend());
    } else {
      builder = testForR8(parameters.getBackend());
    }
    return builder.addProgramClasses(CLASSES).addKeepMainRule(MAIN_CLASS);
  }

  @Test
  public void testConditionalOnClass() throws Exception {
    assumeTrue(optionalShrinker.isPresent());
    Shrinker shrinker = optionalShrinker.get();
    TestRunResult<?> result =
        buildShrinker(shrinker)
            .addKeepRules(
                "-if class "
                    + A.class.getTypeName()
                    + " -keep class "
                    + B.class.getTypeName()
                    + " { void <init>(); void method(); }")
            .compile()
            .run(parameters.getRuntime(), MAIN_CLASS, B.class.getTypeName());
    if (shrinker != Shrinker.Proguard) {
      // TODO(b/160136641): The conditional rule fails to apply after class A is inlined/removed.
      result.assertFailureWithErrorThatThrows(ClassNotFoundException.class);
    } else {
      result.assertSuccessWithOutput(EXPECTED);
    }
  }

  @Test
  public void testConditionalOnClassAndMethod() throws Exception {
    assumeTrue(optionalShrinker.isPresent());
    Shrinker shrinker = optionalShrinker.get();
    TestRunResult<?> result =
        buildShrinker(shrinker)
            .addKeepRules(
                "-if class "
                    + A.class.getTypeName()
                    + " { void method(java.lang.String); }"
                    + " -keep class "
                    + B.class.getTypeName()
                    + " { void <init>(); void method(); }")
            .compile()
            .run(parameters.getRuntime(), MAIN_CLASS, B.class.getTypeName());
    if (shrinker == Shrinker.Proguard) {
      // In this case PG appears to not actually keep the consequent, but it does remain renamed
      // in the output.
      result.assertFailureWithErrorThatThrows(ClassNotFoundException.class);
    } else {
      result.assertSuccessWithOutput(EXPECTED);
    }
  }
}
