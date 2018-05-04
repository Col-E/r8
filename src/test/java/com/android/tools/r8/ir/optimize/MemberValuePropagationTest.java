// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationException;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.code.Const4;
import com.android.tools.r8.code.InvokeDirect;
import com.android.tools.r8.code.IputObject;
import com.android.tools.r8.code.NewInstance;
import com.android.tools.r8.code.ReturnVoid;
import com.android.tools.r8.code.SputObject;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MemberValuePropagationTest {
  private static final String WRITE_ONLY_FIELD = "write_only_field";
  private static final Path EXAMPLE_JAR = Paths.get(ToolHelper.EXAMPLES_BUILD_DIR)
      .resolve(WRITE_ONLY_FIELD + FileUtils.JAR_EXTENSION);
  private static final Path EXAMPLE_KEEP = Paths.get(ToolHelper.EXAMPLES_DIR)
      .resolve(WRITE_ONLY_FIELD).resolve("keep-rules.txt");
  private static final Path DONT_OPTIMIZE = Paths.get(ToolHelper.EXAMPLES_DIR)
      .resolve(WRITE_ONLY_FIELD).resolve("keep-rules-dontoptimize.txt");

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void testWriteOnlyField_putObject_gone() throws Exception {
    Path processedApp = runR8(EXAMPLE_KEEP);
    DexInspector inspector = new DexInspector(processedApp);
    ClassSubject clazz = inspector.clazz(WRITE_ONLY_FIELD + ".WriteOnlyCls");
    clazz.forAllMethods(
        methodSubject -> {
          if (methodSubject.isClassInitializer()) {
            DexEncodedMethod encodedMethod = methodSubject.getMethod();
            DexCode code = encodedMethod.getCode().asDexCode();
            assertEquals(4, code.instructions.length);
            assertTrue(code.instructions[0] instanceof NewInstance);
            assertTrue(code.instructions[1] instanceof Const4);
            assertTrue(code.instructions[2] instanceof InvokeDirect);
            assertTrue(code.instructions[3] instanceof ReturnVoid);
          }
          if (methodSubject.isInstanceInitializer()) {
            DexEncodedMethod encodedMethod = methodSubject.getMethod();
            DexCode code = encodedMethod.getCode().asDexCode();
            assertEquals(4, code.instructions.length);
            assertTrue(code.instructions[0] instanceof InvokeDirect);
            assertTrue(code.instructions[1] instanceof NewInstance);
            assertTrue(code.instructions[2] instanceof InvokeDirect);
            assertTrue(code.instructions[3] instanceof ReturnVoid);
          }
        });
  }

  @Test
  public void testWriteOnlyField_dontoptimize() throws Exception {
    Path processedApp = runR8(DONT_OPTIMIZE);
    DexInspector inspector = new DexInspector(processedApp);
    ClassSubject clazz = inspector.clazz(WRITE_ONLY_FIELD + ".WriteOnlyCls");
    clazz.forAllMethods(
        methodSubject -> {
          if (methodSubject.isClassInitializer()) {
            DexEncodedMethod encodedMethod = methodSubject.getMethod();
            DexCode code = encodedMethod.getCode().asDexCode();
            assertEquals(5, code.instructions.length);
            assertTrue(code.instructions[0] instanceof NewInstance);
            assertTrue(code.instructions[1] instanceof Const4);
            assertTrue(code.instructions[2] instanceof InvokeDirect);
            assertTrue(code.instructions[3] instanceof SputObject);
            assertTrue(code.instructions[4] instanceof ReturnVoid);
          }
          if (methodSubject.isInstanceInitializer()) {
            DexEncodedMethod encodedMethod = methodSubject.getMethod();
            DexCode code = encodedMethod.getCode().asDexCode();
            assertEquals(5, code.instructions.length);
            assertTrue(code.instructions[0] instanceof InvokeDirect);
            assertTrue(code.instructions[1] instanceof NewInstance);
            assertTrue(code.instructions[2] instanceof InvokeDirect);
            assertTrue(code.instructions[3] instanceof IputObject);
            assertTrue(code.instructions[4] instanceof ReturnVoid);
          }
        });
  }

  private Path runR8(Path proguardConfig)
      throws IOException, CompilationException, CompilationFailedException {
    Path dexOutputDir = temp.newFolder().toPath();
    ToolHelper.runR8(
        R8Command.builder()
            .setOutput(dexOutputDir, OutputMode.DexIndexed)
            .addProgramFiles(EXAMPLE_JAR)
            .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
            .addProguardConfigurationFiles(proguardConfig)
            .setDisableMinification(true)
            .build(),
        null);
    return dexOutputDir.resolve("classes.dex");
  }
}
