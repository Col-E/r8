// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.longcompare;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.dexinspector.ClassSubject;
import com.android.tools.r8.utils.dexinspector.DexInspector;
import com.android.tools.r8.utils.dexinspector.InstructionSubject;
import com.android.tools.r8.utils.dexinspector.InvokeInstructionSubject;
import com.android.tools.r8.utils.dexinspector.MethodSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RequireNonNullRewriteTest {

  @Rule
  public TemporaryFolder tmpOutputDir = ToolHelper.getTemporaryFolderForTest();

  void compileWithD8(Path intputPath, Path outputPath, CompilationMode mode)
      throws IOException, CompilationFailedException {
    D8.run(
        D8Command.builder()
            .setMode(mode)
            .addProgramFiles(intputPath)
            .setOutput(outputPath, OutputMode.DexIndexed)
            .build());
  }

  void runTest(Path jarFile, Path dexFile) {
    String mainClass = "rewrite.RequireNonNull";
    ArtCommandBuilder builder = new ArtCommandBuilder(ToolHelper.getDexVm());
    builder.appendClasspath(dexFile.toString());
    builder.setMainClass(mainClass);
    try {
      String output = ToolHelper.runArt(builder);
      ProcessResult javaResult = ToolHelper.runJava(jarFile, mainClass);
      Assert.assertEquals(javaResult.stdout, output);
    } catch (IOException e) {
      Assert.fail();
    }
  }

  @Test
  public void testDebugRequireNonNullIsRewritten() throws Exception {
    runTest(CompilationMode.DEBUG);
  }

  @Test
  public void testReleaseRequireNonNullIsRewritten() throws Exception {
    runTest(CompilationMode.RELEASE);
  }

  private void runTest(CompilationMode mode)
      throws IOException, ExecutionException, CompilationFailedException {
    final Path inputPath = Paths.get(ToolHelper.EXAMPLES_BUILD_DIR + "/rewrite.jar");
    Path outputPath = tmpOutputDir.newFolder().toPath();

    compileWithD8(inputPath, outputPath, mode);

    Path dexPath = outputPath.resolve("classes.dex");

    DexInspector dexInspector = new DexInspector(dexPath);
    ClassSubject classSubject = dexInspector.clazz("rewrite.RequireNonNull");
    MethodSubject methodSubject = classSubject
        .method("java.lang.Object", "nonnullRemove", Collections.emptyList());

    Iterator<InvokeInstructionSubject> iterator =
        methodSubject.iterateInstructions(InstructionSubject::isInvoke);
    Assert.assertTrue(iterator.hasNext());
    Assert.assertEquals("getClass", iterator.next().invokedMethod().name.toString());
    Assert.assertFalse(iterator.hasNext());

    runTest(inputPath, dexPath);
  }
}
