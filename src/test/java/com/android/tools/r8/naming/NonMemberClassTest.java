// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collection;
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
        @NeverInline
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

    public String getExpectedOutput(TestParameters parameters, boolean isFullMode) {
      // In full mode, we remove all attributes since nothing pinned, i.e., no reflection uses.
      if (isFullMode) {
        return "";
      }
      switch (this) {
        case KEEP_INNER_CLASSES:
        case NO_KEEP_NO_MINIFICATION:
          return JVM_OUTPUT;
        case KEEP_ALLOW_MINIFICATION:
        case NO_KEEP_MINIFICATION:
          if (parameters.isCfRuntime()
              && parameters.getRuntime().asCf().getVm().lessThanOrEqual(CfVm.JDK8)) {
            return MINIFIED_OUTPUT_JDK8;
          }
          return JVM_OUTPUT;
        default:
          throw new Unreachable();
      }
    }

    public void inspect(boolean isFullMode, CodeInspector inspector) {
      int expectedNumberOfNonMemberInnerClasses = isFullMode ? 0 : 2;
      assertEquals(
          expectedNumberOfNonMemberInnerClasses,
          inspector.allClasses().stream()
              .filter(
                  classSubject ->
                      classSubject.getDexProgramClass().isLocalClass()
                          || classSubject.getDexProgramClass().isAnonymousClass())
              .count());
    }
  }

  private static final Class<?> MAIN = TestMain.class;

  // Since JDK9, a class is determined as anonymous if the inner-name in the associated inner-class
  // attribute is empty.
  // JDK8 determines an anonymous class differently: checking if a simple name is empty.
  // Moreover, it computes the simple name differently: some assumptions about non-member classes,
  // e.g., 1 or more digits (followed by the simple name if it's local).
  // Since JDK9, the simple name is computed by stripping off the package name.
  // See b/132808897 for more details.
  private static final String MINIFIED_OUTPUT_JDK8 = StringUtils.lines(
      "I'm local."
  );
  private static final String JVM_OUTPUT = StringUtils.lines(
      "I'm local.",
      "I'm anonymous."
  );

  private final TestParameters parameters;
  private final TestConfig config;

  @Parameterized.Parameters(name = "{0} {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevels().build(), TestConfig.values());
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
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JVM_OUTPUT);
  }

  @Test
  public void testR8Compat() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(NonMemberClassTest.class)
        .addKeepMainRule(MAIN)
        .addKeepRules(config.getKeepRules())
        .addKeepAttributes("Signature", "InnerClasses", "EnclosingMethod")
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .addOptionsModification(options -> options.enableClassInlining = false)
        .compile()
        .inspect(inspector -> config.inspect(false, inspector))
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(config.getExpectedOutput(parameters, false));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(NonMemberClassTest.class)
        .addKeepMainRule(MAIN)
        .addKeepRules(config.getKeepRules())
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .addKeepAttributeSignature()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .addOptionsModification(options -> options.enableClassInlining = false)
        .compile()
        .inspect(inspector -> config.inspect(true, inspector))
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(config.getExpectedOutput(parameters, true));
  }
}
