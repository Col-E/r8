// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase.MinifyMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests renaming of class and method names and corresponding proguard map output. */
@RunWith(Parameterized.class)
public class MinificationTest extends DebugTestBase {

  private static final String SOURCE_FILE = "Minified.java";

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

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  private final MinifyMode minificationMode;
  private final boolean writeProguardMap;

  public MinificationTest(MinifyMode minificationMode, boolean writeProguardMap) throws Exception {
    this.minificationMode = minificationMode;
    this.writeProguardMap = writeProguardMap;
  }

  private boolean minifiedNames() {
    return minificationMode.isMinify() && !writeProguardMap;
  }

  private DebugTestConfig getTestConfig() throws Throwable {
    List<String> proguardConfigurations = Collections.emptyList();
    if (minificationMode.isMinify()) {
      ImmutableList.Builder<String> builder = ImmutableList.builder();
      builder.add("-keep public class Minified { public static void main(java.lang.String[]); }");
      builder.add("-keepattributes SourceFile");
      builder.add("-keepattributes LineNumberTable");
      if (minificationMode == MinifyMode.AGGRESSIVE) {
        builder.add("-overloadaggressively");
      }
      proguardConfigurations = builder.build();
    }

    int minSdk = ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm());
    Path dexOutputDir = temp.newFolder().toPath();
    Path proguardMap = writeProguardMap ? dexOutputDir.resolve("proguard.map") : null;
    R8Command.Builder builder =
        R8Command.builder()
            .addProgramFiles(DEBUGGEE_JAR)
            .setOutputPath(dexOutputDir)
            .setMinApiLevel(minSdk)
            .setMode(CompilationMode.DEBUG)
            .addLibraryFiles(Paths.get(ToolHelper.getAndroidJar(minSdk)));
    if (proguardMap != null) {
      builder.setProguardMapOutput(proguardMap);
    }
    if (!proguardConfigurations.isEmpty()) {
      builder.addProguardConfiguration(proguardConfigurations, Origin.unknown());
    }
    ToolHelper.runR8(builder.build());

    D8BaseDebugTestConfig config =
        new D8BaseDebugTestConfig(temp, dexOutputDir.resolve("classes.dex"));
    config.setProguardMap(proguardMap);
    return config;
  }

  @Test
  public void testBreakInMainClass() throws Throwable {
    final String className = "Minified";
    final String methodName = minifiedNames() ? "a" : "test";
    final String signature = "()V";
    final String innerClassName = minifiedNames() ? "a" : "Minified$Inner";
    final String innerMethodName = minifiedNames() ? "a" : "innerTest";
    final String innerSignature = "()I";
    runDebugTest(
        getTestConfig(),
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
    final String innerClassName = minifiedNames() ? "a" : "Minified$Inner";
    final String innerMethodName = minifiedNames() ? "a" : "innerTest";
    final String innerSignature = "()I";
    runDebugTest(
        getTestConfig(),
        className,
        breakpoint(innerClassName, innerMethodName, innerSignature),
        run(),
        checkMethod(innerClassName, innerMethodName, innerSignature),
        checkLine(SOURCE_FILE, 8),
        run());
  }
}
