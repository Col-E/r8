// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.logging;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.AssumeNoClassInitializationSideEffects;
import com.android.tools.r8.AssumeNoSideEffects;
import com.android.tools.r8.AssumeNotNull;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CustomLoggingFrameworkRemovalTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .build();
  }

  public CustomLoggingFrameworkRemovalTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules("-assumenosideeffects class java.lang.StringBuilder { void <init>(int); }")
        .enableAssumeNotNullAnnotations()
        .enableAssumeNoClassInitializationSideEffectsAnnotations()
        .enableAssumeNoSideEffectsAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .addDontObfuscate()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              MethodSubject mainMethodSubject = mainClassSubject.mainMethod();
              assertThat(mainMethodSubject, isPresent());
              assertTrue(
                  mainMethodSubject
                      .streamInstructions()
                      .allMatch(InstructionSubject::isReturnVoid));

              MethodSubject clinitMethodSubject = mainClassSubject.clinit();
              assertThat(clinitMethodSubject, isAbsent());

              assertThat(inspector.clazz(Logger.class), isAbsent());
              assertThat(inspector.clazz(LoggerFactory.class), isAbsent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  public static class Main {

    @AssumeNotNull private static final Logger logger = LoggerFactory.getLogger("MainActivity");

    public static void main(String[] args) {
      logger.print(new StringBuilder(getInt(5)).append("Hello").toString());
      logger.print(Objects.toString(new StringBuilder(getInt(6)).append(" world")));
      logger.print(String.valueOf(new StringBuilder(getInt(1)).append("!")));
    }

    @AssumeNoSideEffects
    @NeverInline
    private static int getInt(int value) {
      return System.currentTimeMillis() > 0 ? value : -1;
    }
  }

  public static class Logger {

    private Logger() {}

    @AssumeNoSideEffects
    public void print(String message) {
      System.out.print(message);
    }
  }

  @AssumeNoClassInitializationSideEffects
  public static class LoggerFactory {

    private static final Map<String, Logger> loggers = new HashMap<>();

    @AssumeNoSideEffects
    static synchronized Logger getLogger(String key) {
      return loggers.computeIfAbsent(key, ignore -> new Logger());
    }
  }
}
