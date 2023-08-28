// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.logging;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.DuplicateTypeInProgramAndLibraryDiagnostic;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AndroidLogRemovalTest extends TestBase {

  enum ClassSpecification {
    ANNOTATED_CLASSES,
    ANNOTATED_METHODS,
    MAIN_METHOD,
    NONE;

    public String getClassSpecification() {
      switch (this) {
        case ANNOTATED_CLASSES:
          return "@" + StripLogs.class.getTypeName() + " class * { <methods>; }";
        case ANNOTATED_METHODS:
          return "class * { @" + StripLogs.class.getTypeName() + " <methods>; }";
        case MAIN_METHOD:
          return "class " + Main.class.getTypeName() + " { public static void main(...); }";
        case NONE:
          return "";
        default:
          throw new Unreachable();
      }
    }
  }

  @Parameter(0)
  public ClassSpecification classSpecifiction;

  @Parameter(1)
  public boolean keepLogs;

  @Parameter(2)
  public int maxRemovedAndroidLogLevel;

  @Parameter(3)
  public TestParameters parameters;

  @Parameters(name = "{3}, class spec: {0}, keep logs: {1}, log level: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        ClassSpecification.values(),
        BooleanUtils.values(),
        ImmutableList.of(1, 2, 3, 4, 5, 6, 7),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    // Only test @KeepLogs with one log level variant.
    assumeTrue(!keepLogs || maxRemovedAndroidLogLevel == 7);

    Path libraryFile =
        testForR8(parameters.getBackend())
            .addProgramClassFileData(
                transformer(Log.class).setClassDescriptor("Landroid/util/Log;").transform())
            .addKeepAllClassesRule()
            .setMinApi(parameters)
            .allowDiagnosticInfoMessages(parameters.isDexRuntime())
            .compileWithExpectedDiagnostics(
                diagnostics -> {
                  if (parameters.isDexRuntime()) {
                    diagnostics.assertAllInfosMatch(
                        diagnosticType(DuplicateTypeInProgramAndLibraryDiagnostic.class));
                  } else {
                    diagnostics.assertNoMessages();
                  }
                })
            .writeToZip();

    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(Main.class)
                .transformMethodInsnInMethod(
                    "main",
                    (opcode, owner, name, descriptor, isInterface, continuation) ->
                        continuation.visitMethodInsn(
                            opcode,
                            owner.endsWith("$Log") ? "android/util/Log" : owner,
                            name,
                            descriptor,
                            isInterface))
                .transform())
        .addProgramClasses(KeepLogs.class, StripLogs.class)
        .addLibraryFiles(libraryFile, parameters.getDefaultRuntimeLibrary())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-maximumremovedandroidloglevel "
                + maxRemovedAndroidLogLevel
                + " "
                + classSpecifiction.getClassSpecification())
        .applyIf(
            keepLogs,
            testBuilder ->
                testBuilder.addKeepRules(
                    "-maximumremovedandroidloglevel 1 class " + Main.class.getTypeName() + " {",
                    "  @" + KeepLogs.class.getTypeName() + " <methods>;",
                    "}"))
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(libraryFile)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(getExpectedOutputForMaxLogLevel());
  }

  private String getExpectedOutputForMaxLogLevel() {
    switch (keepLogs ? 1 : maxRemovedAndroidLogLevel) {
      case 1:
        return StringUtils.join(
            "",
            StringUtils.times(StringUtils.lines("[R8] VERBOSE."), 2),
            StringUtils.times(StringUtils.lines("[R8] DEBUG."), 2),
            StringUtils.times(StringUtils.lines("[R8] INFO."), 2),
            StringUtils.times(StringUtils.lines("[R8] WARN."), 2),
            StringUtils.times(StringUtils.lines("[R8] ERROR."), 2),
            StringUtils.times(StringUtils.lines("[R8] ASSERT."), 2));
      case 2:
        return StringUtils.join(
            "",
            StringUtils.times(StringUtils.lines("[R8] DEBUG."), 2),
            StringUtils.times(StringUtils.lines("[R8] INFO."), 2),
            StringUtils.times(StringUtils.lines("[R8] WARN."), 2),
            StringUtils.times(StringUtils.lines("[R8] ERROR."), 2),
            StringUtils.times(StringUtils.lines("[R8] ASSERT."), 2));
      case 3:
        return StringUtils.join(
            "",
            StringUtils.times(StringUtils.lines("[R8] INFO."), 2),
            StringUtils.times(StringUtils.lines("[R8] WARN."), 2),
            StringUtils.times(StringUtils.lines("[R8] ERROR."), 2),
            StringUtils.times(StringUtils.lines("[R8] ASSERT."), 2));
      case 4:
        return StringUtils.join(
            "",
            StringUtils.times(StringUtils.lines("[R8] WARN."), 2),
            StringUtils.times(StringUtils.lines("[R8] ERROR."), 2),
            StringUtils.times(StringUtils.lines("[R8] ASSERT."), 2));
      case 5:
        return StringUtils.join(
            "",
            StringUtils.times(StringUtils.lines("[R8] ERROR."), 2),
            StringUtils.times(StringUtils.lines("[R8] ASSERT."), 2));
      case 6:
        return StringUtils.join("", StringUtils.times(StringUtils.lines("[R8] ASSERT."), 2));
      case 7:
        return "";
      default:
        throw new Unreachable();
    }
  }

  @StripLogs
  static class Main {

    @KeepLogs
    @StripLogs
    public static void main(String[] args) {
      Log.v("R8", "VERBOSE.");
      if (Log.isLoggable("R8", Log.VERBOSE)) {
        System.out.println("[R8] VERBOSE.");
      }

      Log.d("R8", "DEBUG.");
      if (Log.isLoggable("R8", Log.DEBUG)) {
        System.out.println("[R8] DEBUG.");
      }

      Log.i("R8", "INFO.");
      if (Log.isLoggable("R8", Log.INFO)) {
        System.out.println("[R8] INFO.");
      }

      Log.w("R8", "WARN.");
      if (Log.isLoggable("R8", Log.WARN)) {
        System.out.println("[R8] WARN.");
      }

      Log.e("R8", "ERROR.");
      if (Log.isLoggable("R8", Log.ERROR)) {
        System.out.println("[R8] ERROR.");
      }

      Log.wtf("R8", "ASSERT.");
      if (Log.isLoggable("R8", Log.ASSERT)) {
        System.out.println("[R8] ASSERT.");
      }
    }
  }

  public static class Log {

    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;
    public static final int ASSERT = 7;

    public static boolean isLoggable(String tag, int level) {
      return true;
    }

    public static int v(String tag, String message) {
      System.out.println("[" + tag + "] " + message);
      return 42;
    }

    public static int d(String tag, String message) {
      System.out.println("[" + tag + "] " + message);
      return 42;
    }

    public static int i(String tag, String message) {
      System.out.println("[" + tag + "] " + message);
      return 42;
    }

    public static int w(String tag, String message) {
      System.out.println("[" + tag + "] " + message);
      return 42;
    }

    public static int e(String tag, String message) {
      System.out.println("[" + tag + "] " + message);
      return 42;
    }

    public static int wtf(String tag, String message) {
      System.out.println("[" + tag + "] " + message);
      return 42;
    }
  }

  @Retention(RetentionPolicy.CLASS)
  @interface KeepLogs {}

  @Retention(RetentionPolicy.CLASS)
  @interface StripLogs {}
}
