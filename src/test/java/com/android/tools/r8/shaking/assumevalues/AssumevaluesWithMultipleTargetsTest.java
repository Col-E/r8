// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumevalues;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@NeverClassInline
class Seed {
  // The return value will be replaced with -assumenosideeffects rule
  @NeverInline
  static int seed() {
    return (int) System.currentTimeMillis() % 10;
  }
}

interface Itf {
  int m();
}

@NeverClassInline
class Impl1 implements Itf {
  @NeverInline
  @Override
  public int m() {
    return Seed.seed() % 100;
  }
}

@NeverClassInline
class Impl2 implements Itf {
  @NeverInline
  @Override
  public int m() {
    return Seed.seed() % 10;
  }
}

class ImplConsumer {

  @NeverInline
  private static void testInvokeInterface(Itf itf) {
    System.out.println(itf.m());
  }

  public static void main(String... args) {
    Itf instance = new Impl1();
    // invoke-interface, but devirtualized.
    System.out.println(instance.m());
    // invoke-interface, no single call target.
    testInvokeInterface(instance);
    Impl2 anotherInstance = new Impl2();
    // invoke-virtual, single call target.
    System.out.println(anotherInstance.m());
    System.out.println("The end");
  }
}

@RunWith(Parameterized.class)
public class AssumevaluesWithMultipleTargetsTest extends TestBase {
  private static final Class<?> MAIN = ImplConsumer.class;

  enum TestConfig {
    RULE_THAT_DIRECTLY_REFERS_CLASS,
    RULE_THAT_DIRECTLY_REFERS_INTERFACE,
    RULE_WITH_IMPLEMENTS;

    private static final String SEED_RULE = StringUtils.lines(
        "-assumenosideeffects class **.Seed {",
        "  static int seed() return 42;",
        "}"
    );

    public String getKeepRule() {
      switch (this) {
        case RULE_THAT_DIRECTLY_REFERS_CLASS:
          return StringUtils.lines(
              SEED_RULE,
              "-assumevalues class **.Itf {",
              "  int m() return 8;",
              "}");
        case RULE_THAT_DIRECTLY_REFERS_INTERFACE:
          return StringUtils.lines(
              SEED_RULE,
              "-assumevalues interface **.Itf {",
              "  int m() return 8;",
              "}");
        case RULE_WITH_IMPLEMENTS:
          return StringUtils.lines(
              SEED_RULE,
              "-assumevalues class * implements **.Itf {",
              "  int m() return 8;",
              "}");
      }
      throw new Unreachable();
    }

    private static final String OUTPUT_WITH_PARTIAL_REPLACEMENT = StringUtils.lines(
        "8",
        // TODO(b/132216744): upwards propagation of member rules.
        "42",
        "8",
        "The end"
    );
    private static final String OUTPUT_WITH_FULL_REPLACEMENT = StringUtils.lines(
        "8",
        "8",
        "8",
        "The end"
    );

    public String expectedOutput() {
      switch (this) {
        case RULE_THAT_DIRECTLY_REFERS_CLASS:
        case RULE_THAT_DIRECTLY_REFERS_INTERFACE:
          return OUTPUT_WITH_FULL_REPLACEMENT;
        case RULE_WITH_IMPLEMENTS:
          return OUTPUT_WITH_PARTIAL_REPLACEMENT;
        default:
          throw new Unreachable();
      }
    }

    public void inspect(CodeInspector inspector) {
      ClassSubject main = inspector.clazz(MAIN);
      assertThat(main, isPresent());

      MethodSubject mainMethod = main.mainMethod();
      assertThat(mainMethod, isPresent());
      assertEquals(
          0,
          Streams.stream(mainMethod.iterateInstructions(
              i -> i.isInvoke() && i.getMethod().name.toString().equals("m"))).count());

      MethodSubject testInvokeInterface = main.uniqueMethodWithName("testInvokeInterface");
      assertThat(testInvokeInterface, isPresent());
      // TODO(b/132216744): upwards propagation of member rules.
      assertEquals(
          1,
          Streams.stream(testInvokeInterface.iterateInstructions(
              i -> i.isInvoke() && i.getMethod().name.toString().equals("m"))).count());
    }
  }

  @Parameterized.Parameters(name = "{0} {1}")
  public static Collection<Object[]> data() {
    return buildParameters(getTestParameters().withAllRuntimes().build(), TestConfig.values());
  }

  private final TestParameters parameters;
  private final TestConfig config;

  public AssumevaluesWithMultipleTargetsTest(TestParameters parameters, TestConfig config) {
    this.parameters = parameters;
    this.config = config;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(MAIN, Seed.class, Itf.class, Impl1.class, Impl2.class)
        .enableClassInliningAnnotations()
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules(config.getKeepRule())
        .noMinification()
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(config.expectedOutput())
        .inspect(config::inspect);
  }
}
