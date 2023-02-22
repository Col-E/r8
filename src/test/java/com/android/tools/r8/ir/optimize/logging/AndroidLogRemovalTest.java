// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.logging;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AndroidLogRemovalTest extends TestBase {

  private final int maxRemovedAndroidLogLevel;
  private final TestParameters parameters;

  @Parameters(name = "{1}, log level: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ImmutableList.of(1, 2, 3, 4, 5, 6, 7),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public AndroidLogRemovalTest(int maxRemovedAndroidLogLevel, TestParameters parameters) {
    this.maxRemovedAndroidLogLevel = maxRemovedAndroidLogLevel;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Path libraryFile =
        testForR8(parameters.getBackend())
            .addProgramClassFileData(
                transformer(Log.class).setClassDescriptor("Landroid/util/Log;").transform())
            .addKeepAllClassesRule()
            .setMinApi(parameters)
            .compile()
            .writeToZip();

    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(TestClass.class)
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
        .addLibraryFiles(libraryFile, parameters.getDefaultRuntimeLibrary())
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-maximumremovedandroidloglevel " + maxRemovedAndroidLogLevel)
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(libraryFile)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(getExpectedOutputForMaxLogLevel());
  }

  private String getExpectedOutputForMaxLogLevel() {
    switch (maxRemovedAndroidLogLevel) {
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

  static class TestClass {

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
}
