// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.ProguardRuleParserException;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Assert;

public class AsmTestBase extends TestBase {

  protected void ensureException(String main, Class<? extends Throwable> exceptionClass,
      byte[]... classes) throws Exception {
    ensureExceptionThrown(runOnJava(main, classes), exceptionClass);
    AndroidApp app = buildAndroidApp(classes);
    ensureExceptionThrown(runOnArtRaw(compileWithD8(app), main), exceptionClass);
    ensureExceptionThrown(runOnArtRaw(compileWithR8(app), main), exceptionClass);
    ensureExceptionThrown(
        runOnArtRaw(compileWithR8(app, keepMainProguardConfiguration(main) + "-dontobfuscate\n"),
            main),
        exceptionClass);
  }

  protected void ensureSameOutput(String main, int apiLevel, byte[]... classes) throws Exception {
    AndroidApp app = buildAndroidApp(classes);
    Consumer<InternalOptions> setMinApiLevel = o -> o.minApiLevel = apiLevel;
    ProcessResult javaResult = runOnJava(main, classes);
    ProcessResult d8Result = runOnArtRaw(compileWithD8(app, setMinApiLevel), main);
    ProcessResult r8Result = runOnArtRaw(compileWithR8(app, setMinApiLevel), main);
    ProcessResult r8ShakenResult = runOnArtRaw(
        compileWithR8(app, keepMainProguardConfiguration(main) + "-dontobfuscate\n",
            setMinApiLevel), main);
    Assert.assertEquals(javaResult.stdout, d8Result.stdout);
    Assert.assertEquals(javaResult.stdout, r8Result.stdout);
    Assert.assertEquals(javaResult.stdout, r8ShakenResult.stdout);
  }

  protected void ensureSameOutput(String main, byte[]... classes) throws Exception {
    AndroidApp app = buildAndroidApp(classes);
    ensureSameOutput(main, app, classes);
  }

  private void ensureSameOutput(String main, AndroidApp app, byte[]... classes)
      throws IOException, CompilationException, ExecutionException, CompilationFailedException,
      ProguardRuleParserException {
    ProcessResult javaResult = runOnJava(main, classes);
    ProcessResult d8Result = runOnArtRaw(compileWithD8(app), main);
    ProcessResult r8Result = runOnArtRaw(compileWithR8(app), main);
    ProcessResult r8ShakenResult = runOnArtRaw(
        compileWithR8(app, keepMainProguardConfiguration(main) + "-dontobfuscate\n"), main);
    Assert.assertEquals(javaResult.stdout, d8Result.stdout);
    Assert.assertEquals(javaResult.stdout, r8Result.stdout);
    Assert.assertEquals(javaResult.stdout, r8ShakenResult.stdout);
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
      throws IOException, CompilationException, ExecutionException,
      CompilationFailedException, ProguardRuleParserException {
    AndroidApp app = buildAndroidApp(classes);
    // Compile to dex files with D8.
    AndroidApp dexApp = compileWithD8(app);
    // Perform dex merging with D8 to read the dex files.
    AndroidApp mergedApp = compileWithD8(dexApp);
    ensureSameOutput(main, mergedApp, classes);
  }

  protected AndroidApp buildAndroidApp(byte[]... classes) throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    for (byte[] clazz : classes) {
      builder.addClassProgramData(clazz, Origin.unknown());
    }
    builder.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.N.getLevel()));
    return builder.build();
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

  protected ProcessResult runOnJava(String main, byte[]... classes) throws IOException {
    Path file = writeToZip(classes);
    return ToolHelper.runJavaNoVerify(file, main);
  }

  private Path writeToZip(byte[]... classes) throws IOException {
    DumpLoader dumpLoader = new DumpLoader();
    File result = temp.newFile();
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(result.toPath(),
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
      for (byte[] clazz : classes) {
        String name = dumpLoader.loadClass(clazz).getTypeName();
        ZipEntry zipEntry = new ZipEntry(DescriptorUtils.getPathFromJavaType(name));
        zipEntry.setSize(clazz.length);
        out.putNextEntry(zipEntry);
        out.write(clazz);
        out.closeEntry();
      }
    }
    return result.toPath();
  }

  protected static Class loadClassFromDump(byte[] dump) {
    return new DumpLoader().loadClass(dump);
  }

  @FunctionalInterface
  protected interface AsmDump {

    byte[] dump() throws Exception;
  }

  protected static Class loadClassFromAsmClass(AsmDump asmDump) {
    try {
      return new DumpLoader().loadClass(asmDump.dump());
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

  private static class DumpLoader extends ClassLoader {

    @SuppressWarnings("deprecation")
    public Class loadClass(byte[] clazz) {
      return defineClass(clazz, 0, clazz.length);
    }
  }

}
