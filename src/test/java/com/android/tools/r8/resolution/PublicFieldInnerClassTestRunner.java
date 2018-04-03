// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PublicFieldInnerClassTestRunner extends TestBase {
  static final Class CLASS = PublicFieldInnerClassTest.class;
  static final Class<?>[] CLASSES = PublicFieldInnerClassTest.CLASSES;

  @Test
  public void testCf() throws Exception {
    ProcessResult runInput =
        ToolHelper.runJava(ToolHelper.getClassPathForTests(), CLASS.getCanonicalName());
    assertEquals(0, runInput.exitCode);
    Path outCf = temp.getRoot().toPath().resolve("cf.jar");
    build(new ClassFileConsumer.ArchiveConsumer(outCf));
    ProcessResult runCf = ToolHelper.runJava(outCf, CLASS.getCanonicalName());
    assertEquals(runInput.toString(), runCf.toString());
    assertEquals(
        -1,
        runCf.stderr.indexOf("java.lang.NoSuchFieldError"));
  }

  @Test
  public void testDex() throws Exception {
    ProcessResult runInput =
        ToolHelper.runJava(ToolHelper.getClassPathForTests(), CLASS.getCanonicalName());
    assertEquals(0, runInput.exitCode);
    Path outDex = temp.getRoot().toPath().resolve("dex.zip");
    build(new DexIndexedConsumer.ArchiveConsumer(outDex));
    ProcessResult runDex = ToolHelper.runArtNoVerificationErrorsRaw(
        outDex.toString(), CLASS.getCanonicalName());
    assertEquals(runInput.stdout, runDex.stdout);
    assertEquals(runInput.exitCode, runDex.exitCode);
    assertEquals(
        -1,
        runDex.stderr.indexOf("java.lang.NoSuchFieldError"));
  }

  private void build(ProgramConsumer consumer) throws Exception {
    List<String> config = Arrays.asList(
        "-keep public class " + CLASS.getCanonicalName() + " {",
        "  public static void main(...);",
        "}"
    );
    Builder builder = R8Command.builder()
        .setMode(CompilationMode.DEBUG)
        .addLibraryFiles(ToolHelper.getAndroidJar(ToolHelper.getMinApiLevelForDexVm()))
        .setProgramConsumer(consumer)
        .addProguardConfiguration(config, Origin.unknown());
    for (Class<?> c : CLASSES) {
      builder.addClassProgramData(ToolHelper.getClassAsBytes(c), Origin.unknown());
    }
    if (consumer instanceof ClassFileConsumer) {
      // TODO(b/75997473): Enable inlining when supported by CF.
      ToolHelper.runR8(builder.build(), options -> options.enableInlining = false);
    } else {
      ToolHelper.runR8(builder.build());
    }
  }
}
