// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase.MinifyMode;
import com.android.tools.r8.shaking.ProguardRuleParserException;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests renaming of class and method names and corresponding proguard map output. */
@RunWith(Parameterized.class)
public class MinificationTest extends DebugTestBase {

  public static final String SOURCE_FILE = "Minified.java";
  private static final HashMap<Config, Path> debuggeePathMap = new HashMap<>();

  private static class Config {
    public final MinifyMode minificationMode;
    public final boolean writeProguardMap;

    Config(MinifyMode minificationMode, boolean writeProguardMap) {
      this.minificationMode = minificationMode;
      this.writeProguardMap = writeProguardMap;
    }

    public boolean minifiedNames() {
      return minificationMode.isMinify() && !writeProguardMap;
    }
  }

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

  private final Config config;

  private synchronized DebuggeePath getDebuggeePath()
      throws IOException, CompilationException, ExecutionException, ProguardRuleParserException {
    Path path = debuggeePathMap.get(config);
    if (path == null) {
      List<String> proguardConfigurations = Collections.<String>emptyList();
      if (config.minificationMode.isMinify()) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add("-keep public class Minified { public static void main(java.lang.String[]); }");
        builder.add("-keepattributes SourceFile");
        builder.add("-keepattributes LineNumberTable");
        if (config.minificationMode == MinifyMode.AGGRESSIVE) {
          builder.add("-overloadaggressively");
        }
        proguardConfigurations = builder.build();
      }
      path =
          compileToDexViaR8(
              null,
              null,
              DEBUGGEE_JAR,
              proguardConfigurations,
              config.writeProguardMap,
              CompilationMode.DEBUG);
      debuggeePathMap.put(config, path);
    }
    return DebuggeePath.makeDex(path);
  }

  public MinificationTest(MinifyMode minificationMode, boolean writeProguardMap) throws Exception {
    config = new Config(minificationMode, writeProguardMap);
  }

  @Test
  public void testBreakInMainClass() throws Throwable {
    final String className = "Minified";
    final String methodName = config.minifiedNames() ? "a" : "test";
    final String signature = "()V";
    final String innerClassName = config.minifiedNames() ? "a" : "Minified$Inner";
    final String innerMethodName = config.minifiedNames() ? "a" : "innerTest";
    final String innerSignature = "()I";
    runDebugTest(
        getDebuggeePath(),
        className,
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
    final String className = "Minified";
    final String innerClassName = config.minifiedNames() ? "a" : "Minified$Inner";
    final String innerMethodName = config.minifiedNames() ? "a" : "innerTest";
    final String innerSignature = "()I";
    runDebugTest(
        getDebuggeePath(),
        className,
        breakpoint(innerClassName, innerMethodName, innerSignature),
        run(),
        checkMethod(innerClassName, innerMethodName, innerSignature),
        checkLine(SOURCE_FILE, 8),
        run());
  }
}
