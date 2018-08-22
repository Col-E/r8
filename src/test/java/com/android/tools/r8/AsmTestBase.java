// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.ProguardRuleParserException;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Assert;

public class AsmTestBase extends TestBase {

  protected void ensureException(String main, Class<? extends Throwable> exceptionClass,
      byte[]... classes) throws Exception {
    ensureExceptionThrown(runOnJavaRaw(main, classes), exceptionClass);
    AndroidApp app = buildAndroidApp(classes);
    ensureExceptionThrown(runOnArtRaw(compileWithD8(app), main), exceptionClass);
    ensureExceptionThrown(runOnArtRaw(compileWithR8(app), main), exceptionClass);
    ensureExceptionThrown(
        runOnArtRaw(compileWithR8(app, keepMainProguardConfiguration(main) + "-dontobfuscate\n"),
            main),
        exceptionClass);
  }

  protected void ensureSameOutput(String main, AndroidApiLevel apiLevel, byte[]... classes)
      throws Exception {
    ensureSameOutput(main, apiLevel, Collections.emptyList(), classes);
  }

  protected void ensureSameOutput(String main, AndroidApiLevel apiLevel,
      List<String> args, byte[]... classes) throws Exception {
    AndroidApp app = buildAndroidApp(classes);
    Consumer<InternalOptions> setMinApiLevel = o -> o.minApiLevel = apiLevel.getLevel();
    ProcessResult javaResult = runOnJavaRaw(main, Arrays.asList(classes), args);
    Consumer<ArtCommandBuilder> cmdBuilder = builder -> {
      for (String arg : args) {
        builder.appendProgramArgument(arg);
      }
    };
    ProcessResult d8Result = runOnArtRaw(
        compileWithD8(app, setMinApiLevel), main, cmdBuilder, null);
    ProcessResult r8Result = runOnArtRaw(
        compileWithR8(app, setMinApiLevel), main, cmdBuilder, null);
    ProcessResult r8ShakenResult = runOnArtRaw(
        compileWithR8(app, keepMainProguardConfiguration(main) + "-dontobfuscate\n",
            setMinApiLevel), main, cmdBuilder, null);
    Assert.assertEquals(javaResult.stdout, d8Result.stdout);
    Assert.assertEquals(javaResult.stdout, r8Result.stdout);
    Assert.assertEquals(javaResult.stdout, r8ShakenResult.stdout);
  }

  protected void ensureSameOutput(String main, byte[]... classes) throws Exception {
    AndroidApp app = buildAndroidApp(classes);
    ensureSameOutput(main, app, false, classes);
  }

  protected void ensureSameOutputJavaNoVerify(String main, byte[]... classes) throws Exception {
    AndroidApp app = buildAndroidApp(classes);
    ensureSameOutput(main, app, true, classes);
  }

  private void ensureSameOutput(
      String main, AndroidApp app, boolean useJavaNoVerify, byte[]... classes)
      throws IOException, CompilationFailedException {
    ProcessResult javaResult =
        useJavaNoVerify ? runOnJavaRawNoVerify(main, classes) : runOnJavaRaw(main, classes);
    ProcessResult d8Result = runOnArtRaw(compileWithD8(app), main);
    ProcessResult r8Result = runOnArtRaw(compileWithR8(app), main);
    ProcessResult r8ShakenResult = runOnArtRaw(
        compileWithR8(app, keepMainProguardConfiguration(main) + "-dontobfuscate\n"), main);
    Assert.assertEquals(javaResult.stdout, d8Result.stdout);
    Assert.assertEquals(javaResult.stdout, r8Result.stdout);
    Assert.assertEquals(javaResult.stdout, r8ShakenResult.stdout);
    Assert.assertEquals(0, javaResult.exitCode);
    Assert.assertEquals(0, d8Result.exitCode);
    Assert.assertEquals(0, r8Result.exitCode);
    Assert.assertEquals(0, r8ShakenResult.exitCode);
  }

  protected void ensureR8FailsWithCompilationError(String main, byte[]... classes)
      throws Exception {
    // TODO(zerny): Port this to use diagnostics handler.
    AndroidApp app = buildAndroidApp(classes);
    CompilationError r8Error = null;
    CompilationError r8ShakenError = null;
    try {
      runOnArtRaw(compileWithR8(app), main);
    } catch (CompilationError e) {
      r8Error = e;
    }
    try {
      runOnArtRaw(compileWithR8(app, keepMainProguardConfiguration(main) + "-dontobfuscate\n"),
          main);
    } catch (CompilationError e) {
      r8ShakenError = e;
    }
    Assert.assertNotNull(r8Error);
    Assert.assertNotNull(r8ShakenError);
  }

  protected void ensureSameOutputAfterMerging(String main, byte[]... classes)
      throws IOException, CompilationFailedException, ProguardRuleParserException {
    AndroidApp app = buildAndroidApp(classes);
    // Compile to dex files with D8.
    AndroidApp dexApp = compileWithD8(app);
    // Perform dex merging with D8 to read the dex files.
    AndroidApp mergedApp = compileWithD8(dexApp);
    ensureSameOutput(main, mergedApp, false, classes);
  }

  protected static AndroidApp readClassesAndAsmDump(List<Class> classes, List<byte[]> asmClasses)
      throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    for (Class clazz : classes) {
      builder.addProgramFiles(ToolHelper.getClassFileForTestClass(clazz));
    }
    for (byte[] clazz : asmClasses) {
      builder.addClassProgramData(clazz, Origin.unknown());
    }
    return builder.build();
  }

  private void ensureExceptionThrown(ProcessResult result, Class<? extends Throwable> exception) {
    assertFalse(result.stdout, result.exitCode == 0);
    assertTrue(result.stderr, result.stderr.contains(exception.getCanonicalName()));
  }

  @FunctionalInterface
  protected interface AsmDump {

    byte[] dump() throws Exception;
  }

  protected static Class loadClassFromAsmClass(AsmDump asmDump) {
    try {
      return loadClassFromDump(asmDump.dump());
    } catch (Exception e) {
      throw new ClassFormatError(e.toString());
    }
  }

  protected static byte[] getBytesFromAsmClass(AsmDump asmDump) {
    try {
      return asmDump.dump();
    } catch (Exception e) {
      throw new ClassFormatError(e.toString());
    }
  }
}
