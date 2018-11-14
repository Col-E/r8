// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MemberValuePropagationTest {
  private static final String PACKAGE_NAME = "write_only_field";
  private static final String QUALIFIED_CLASS_NAME = PACKAGE_NAME + ".WriteOnlyCls";
  private static final Path EXAMPLE_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR).resolve(PACKAGE_NAME + FileUtils.JAR_EXTENSION);
  private static final Path EXAMPLE_KEEP =
      Paths.get(ToolHelper.EXAMPLES_DIR).resolve(PACKAGE_NAME).resolve("keep-rules.txt");
  private static final Path DONT_OPTIMIZE =
      Paths.get(ToolHelper.EXAMPLES_DIR)
          .resolve(PACKAGE_NAME)
          .resolve("keep-rules-dontoptimize.txt");

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public MemberValuePropagationTest(TestBase.Backend backend) {
    this.backend = backend;
  }

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void testWriteOnlyField_putObject_gone() throws Exception {
    List<Path> processedApp = runR8(EXAMPLE_KEEP);
    CodeInspector inspector = new CodeInspector(processedApp);
    ClassSubject clazz = inspector.clazz(QUALIFIED_CLASS_NAME);
    clazz.forAllMethods(
        methodSubject -> {
          Iterator<InstructionSubject> iterator = methodSubject.iterateInstructions();
          while (iterator.hasNext()) {
            InstructionSubject instruction = iterator.next();
            assertFalse(instruction.isInstancePut() || instruction.isStaticPut());
          }
        });
  }

  @Test
  public void testWriteOnlyField_dontoptimize() throws Exception {
    List<Path> processedApp = runR8(DONT_OPTIMIZE);
    CodeInspector inspector = new CodeInspector(processedApp);
    ClassSubject clazz = inspector.clazz(QUALIFIED_CLASS_NAME);
    assert backend == Backend.DEX || backend == Backend.CF;
    clazz.forAllMethods(
        methodSubject -> {
          Iterator<InstructionSubject> iterator = methodSubject.iterateInstructions();
          if (methodSubject.isClassInitializer()) {
            int numPuts = 0;
            while (iterator.hasNext()) {
              if (iterator.next().isStaticPut()) {
                ++numPuts;
              }
            }
            assertEquals(1, numPuts);
          }
          if (methodSubject.isInstanceInitializer()) {
            int numPuts = 0;
            while (iterator.hasNext()) {
              if (iterator.next().isInstancePut()) {
                ++numPuts;
              }
            }
            assertEquals(1, numPuts);
          }
        });
  }

  private List<Path> runR8(Path proguardConfig) throws IOException, CompilationFailedException {
    Path outputDir = temp.newFolder().toPath();
    assert backend == Backend.DEX || backend == Backend.CF;
    ToolHelper.runR8(
        R8Command.builder()
            .setOutput(outputDir, TestBase.outputMode(backend))
            .addProgramFiles(EXAMPLE_JAR)
            .addLibraryFiles(TestBase.runtimeJar(backend))
            .addProguardConfigurationFiles(proguardConfig)
            .setDisableMinification(true)
            .build(),
        o -> {
          o.enableClassInlining = false;
        });

    return backend == Backend.DEX
        ? Collections.singletonList(outputDir.resolve(Paths.get("classes.dex")))
        : Arrays.stream(
                outputDir
                    .resolve(PACKAGE_NAME)
                    .toFile()
                    .listFiles(f -> f.toString().endsWith(".class")))
            .map(File::toPath)
            .collect(Collectors.toList());
  }
}
