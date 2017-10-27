// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.TestBase.MinifyMode;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests local variable information. */
@RunWith(Parameterized.class)
public class MinificationTest extends DebugTestBase {

  public static final String SOURCE_FILE = "Minified.java";

  @Parameterized.Parameters(name = "minification: {0}, proguardMap: {1}")
  public static Collection minificationControl() {
    ImmutableList.Builder<Object> builder = ImmutableList.builder();
    for (MinifyMode mode : MinifyMode.values()) {
      builder.add((Object) new Object[]{mode, false});
      if (mode.isMinify()) {
        builder.add((Object) new Object[]{mode, true});
      }
    }
    return builder.build();
  }

  private static boolean firstRun = true;
  private static MinifyMode minificationMode;

  public MinificationTest(MinifyMode minificationMode, boolean writeProguardMap) throws Exception {
    // TODO(tamaskenez) The way we're shadowing and calling the static setUp() methods should be
    // updated when we refactor DebugTestBase.
    if (firstRun
        || MinificationTest.minificationMode != minificationMode
        || DebugTestBase.writeProguardMap != writeProguardMap) {

      firstRun = false;
      MinificationTest.minificationMode = minificationMode;
      DebugTestBase.writeProguardMap = writeProguardMap;

      if (MinificationTest.minificationMode.isMinify()) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add("-keep public class Minified { public static void main(java.lang.String[]); }");
        builder.add("-keepattributes SourceFile");
        builder.add("-keepattributes LineNumberTable");
        if (minificationMode == MinifyMode.AGGRESSIVE) {
          builder.add("-overloadaggressively");
        }
        proguardConfigurations = builder.build();
      }
      setUp(null, null);
    }
  }

  @BeforeClass
  public static void setUp() throws Exception {}

  @Test
  public void testBreakInMainClass() throws Throwable {
    boolean minifiedNames = (minificationMode.isMinify() && !writeProguardMap);
    final String className = "Minified";
    final String methodName = minifiedNames ? "a" : "test";
    final String signature = "()V";
    final String innerClassName = minifiedNames ? "a" : "Minified$Inner";
    final String innerMethodName = minifiedNames ? "a" : "innerTest";
    final String innerSignature = "()I";
    runDebugTestR8(className,
        breakpoint(className, methodName, signature),
        run(),
        checkMethod(className, methodName, signature),
        checkLine(SOURCE_FILE, 14),
        stepOver(INTELLIJ_FILTER),
        checkMethod(className, methodName, signature),
        checkLine(SOURCE_FILE, 15),
        stepInto(INTELLIJ_FILTER),
        checkMethod(innerClassName, innerMethodName, innerSignature),
        checkLine(SOURCE_FILE, 8),
        run());
  }

  @Test
  public void testBreakInPossiblyRenamedClass() throws Throwable {
    boolean minifiedNames = (minificationMode.isMinify() && !writeProguardMap);
    final String className = "Minified";
    final String innerClassName = minifiedNames ? "a" : "Minified$Inner";
    final String innerMethodName = minifiedNames ? "a" : "innerTest";
    final String innerSignature = "()I";
    runDebugTestR8(
        className,
        breakpoint(innerClassName, innerMethodName, innerSignature),
        run(),
        checkMethod(innerClassName, innerMethodName, innerSignature),
        checkLine(SOURCE_FILE, 8),
        run());
  }
}
