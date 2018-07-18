// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase.MinifyMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.debug.DebugTestConfig.RuntimeKind;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
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

  @Parameterized.Parameters(name = "backend:{0} minification:{1} proguardMap:{2}")
  public static Collection minificationControl() {
    ImmutableList.Builder<Object> builder = ImmutableList.builder();
    for (RuntimeKind kind : RuntimeKind.values()) {
      for (MinifyMode mode : MinifyMode.values()) {
        builder.add((Object) new Object[] {kind, mode, false});
        if (mode.isMinify()) {
          builder.add((Object) new Object[] {kind, mode, true});
        }
      }
    }
    return builder.build();
  }

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  private final RuntimeKind runtimeKind;
  private final MinifyMode minificationMode;
  private final boolean writeProguardMap;

  public MinificationTest(
      RuntimeKind runtimeKind, MinifyMode minificationMode, boolean writeProguardMap) {
    this.runtimeKind = runtimeKind;
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

    Path outputPath = temp.getRoot().toPath().resolve("classes.zip");
    Path proguardMap = writeProguardMap ? temp.getRoot().toPath().resolve("proguard.map") : null;
    OutputMode outputMode =
        runtimeKind == RuntimeKind.CF ? OutputMode.ClassFile : OutputMode.DexIndexed;
    R8Command.Builder builder =
        R8Command.builder()
            .addProgramFiles(DEBUGGEE_JAR)
            .setOutput(outputPath, outputMode)
            .setMode(CompilationMode.DEBUG);
    if (runtimeKind == RuntimeKind.DEX) {
      AndroidApiLevel minSdk = ToolHelper.getMinApiLevelForDexVm();
      builder.setMinApiLevel(minSdk.getLevel()).addLibraryFiles(ToolHelper.getAndroidJar(minSdk));
    } else if (runtimeKind == RuntimeKind.CF) {
      builder.addLibraryFiles(ToolHelper.getJava8RuntimeJar());
    }
    if (proguardMap != null) {
      builder.setProguardMapOutputPath(proguardMap);
    }
    if (!proguardConfigurations.isEmpty()) {
      builder.addProguardConfiguration(proguardConfigurations, Origin.unknown());
    }
    // Disable line number optimization if we're not using a Proguard map.
    ToolHelper.runR8(
        builder.build(),
        proguardMap == null
            ? (oc -> oc.lineNumberOptimization = LineNumberOptimization.OFF)
            : null);

    switch (runtimeKind) {
      case CF:
        {
          CfDebugTestConfig config = new CfDebugTestConfig(outputPath);
          config.setProguardMap(proguardMap);
          return config;
        }
      case DEX:
        {
          DexDebugTestConfig config = new DexDebugTestConfig(outputPath);
          config.setProguardMap(proguardMap);
          return config;
        }
      default:
        throw new Unreachable();
    }
  }

  @Test
  public void testBreakInMainClass() throws Throwable {
    final String className = "Minified";
    final String methodName = minifiedNames() ? "a" : "test";
    final String signature = "()V";
    final String innerClassName = minifiedNames() ? "a" : "Minified$Inner";
    final String innerMethodName = minifiedNames() ? "a" : "innerTest";
    final String innerSignature = "()I";
    DebugTestConfig config = getTestConfig();
    checkStructure(
        config,
        className,
        MethodSignature.fromSignature(methodName, signature),
        innerClassName,
        MethodSignature.fromSignature(innerMethodName, innerSignature));
    runDebugTest(
        config,
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
    DebugTestConfig config = getTestConfig();
    checkStructure(
        config,
        className,
        innerClassName,
        MethodSignature.fromSignature(innerMethodName, innerSignature));
    runDebugTest(
        config,
        className,
        breakpoint(innerClassName, innerMethodName, innerSignature),
        run(),
        checkMethod(innerClassName, innerMethodName, innerSignature),
        checkLine(SOURCE_FILE, 8),
        run());
  }

  private void checkStructure(
      DebugTestConfig config,
      String className,
      MethodSignature method,
      String innerClassName,
      MethodSignature innerMethod)
      throws Throwable {
    Path proguardMap = config.getProguardMap();
    String mappingFile = proguardMap == null ? null : proguardMap.toString();
    CodeInspector inspector = new CodeInspector(config.getPaths(), mappingFile);
    ClassSubject clazz = inspector.clazz(className);
    assertTrue(clazz.isPresent());
    if (method != null) {
      assertTrue(clazz.method(method).isPresent());
    }
    ClassSubject innerClass = inspector.clazz(innerClassName);
    assertTrue(innerClass.isPresent());
    assertTrue(innerClass.method(innerMethod).isPresent());
  }

  private void checkStructure(
      DebugTestConfig config, String className, String innerClassName, MethodSignature innerMethod)
      throws Throwable {
    checkStructure(config, className, null, innerClassName, innerMethod);
  }
}
