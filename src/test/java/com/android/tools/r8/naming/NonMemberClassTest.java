// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collection;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NonMemberClassTest extends TestBase {

  static class Enclosing {
    @NeverInline
    void foo() {
      class Local {
        Local() {
          if (this.getClass().getEnclosingClass() != null) {
            System.out.println("I'm local.");
          }
        }
      }
      new Local();

      Runnable r = new Runnable() {
        @Override
        public void run() {
          if (this.getClass().isAnonymousClass()) {
            System.out.println("I'm anonymous.");
          }
        }
      };
      r.run();
    }
  }

  static class TestMain {
    public static void main(String... args) {
      new Enclosing().foo();
    }
  }

  enum TestConfig {
    KEEP_INNER_CLASSES,
    KEEP_ALLOW_MINIFICATION,
    NO_KEEP_NO_MINIFICATION,
    NO_KEEP_MINIFICATION;

    public String getKeepRules() {
      switch (this) {
        case KEEP_INNER_CLASSES:
          return "-keep class " + Enclosing.class.getName() + "$*";
        case KEEP_ALLOW_MINIFICATION:
          return "-keep,allowobfuscation class " + Enclosing.class.getName() + "$*";
        case NO_KEEP_NO_MINIFICATION:
          return "-dontobfuscate";
        case NO_KEEP_MINIFICATION:
          return "";
        default:
          throw new Unreachable();
      }
    }
  }

  private static final Class<?> MAIN = TestMain.class;
  private static final String EXPECTED_OUTPUT = StringUtils.lines(
      "I'm local.",
      "I'm anonymous."
  );

  private final TestParameters parameters;
  private final TestConfig config;

  @Parameterized.Parameters(name = "{0} {1}")
  public static Collection<Object[]> data() {
    return buildParameters(getTestParameters().withAllRuntimes().build(), TestConfig.values());
  }

  public NonMemberClassTest(TestParameters parameters, TestConfig config) {
    this.parameters = parameters;
    this.config = config;
  }

  @Test
  public void testJVMOutput() throws Exception {
    assumeTrue(
        "Only run JVM reference on CF runtimes",
        parameters.isCfRuntime() && config == TestConfig.NO_KEEP_NO_MINIFICATION);
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8Compat() throws Exception {
    assumeTrue("b/132128436", config == TestConfig.NO_KEEP_NO_MINIFICATION);
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(NonMemberClassTest.class)
        .addKeepMainRule(MAIN)
        .addKeepRules(config.getKeepRules())
        .addKeepAttributes("InnerClasses", "EnclosingMethod")
        .enableInliningAnnotations()
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(this::inspect);
  }

  @Ignore("b/132128436")
  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(NonMemberClassTest.class)
        .addKeepMainRule(MAIN)
        .addKeepRules(config.getKeepRules())
        .addKeepAttributes("InnerClasses", "EnclosingMethod")
        .enableInliningAnnotations()
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    assertEquals(2,
        inspector.allClasses().stream()
            .filter(classSubject ->
                classSubject.getDexClass().isLocalClass()
                    || classSubject.getDexClass().isAnonymousClass()).count());
  }
}
