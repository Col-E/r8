// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldAccessInstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Function;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import regress_76025099.Main;
import regress_76025099.impl.Impl;

@RunWith(Parameterized.class)
public class B76025099 extends TestBase {

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public B76025099(Backend backend) {
    this.backend = backend;
  }

  private static final String PRG =
      ToolHelper.EXAMPLES_BUILD_DIR + "regress_76025099" + FileUtils.JAR_EXTENSION;

  private AndroidApp runR8(AndroidApp app) throws Exception {
    R8Command command =
        ToolHelper.addProguardConfigurationConsumer(
                ToolHelper.prepareR8CommandBuilder(app, emptyConsumer(backend)),
                pgConfig -> {
                  pgConfig.setPrintMapping(true);
                  pgConfig.setPrintMappingFile(map);
                })
            .addLibraryFiles(runtimeJar(backend))
            .addProguardConfigurationFiles(pgConfig)
            .setOutput(tempRoot.toPath(), outputMode(backend))
            .build();
    return ToolHelper.runR8(command, o -> {
      o.enableMinification = false;
    });
  }

  private File tempRoot;
  private Path jarPath;
  private AndroidApp originalApp;
  private String mainName;
  private Path pgConfig;
  private Path map;

  @Before
  public void setUp() throws Exception {
    tempRoot = temp.getRoot();
    jarPath = Paths.get(PRG);
    originalApp = readJar(jarPath);
    mainName = Main.class.getCanonicalName();
    pgConfig = File.createTempFile("keep-rules", ".config", tempRoot).toPath();
    String config = keepMainProguardConfiguration(Main.class);
    config += System.lineSeparator() + "-dontobfuscate";
    Files.write(pgConfig, config.getBytes());
    map = File.createTempFile("proguard", ".map", tempRoot).toPath();
  }

  @Test
  public void testProguardAndD8() throws Exception {
    Assume.assumeTrue(backend == Backend.DEX);
    if (!isRunProguard()) {
      return;
    }

    ProcessResult jvmOutput = ToolHelper.runJava(ImmutableList.of(jarPath), mainName);
    assertEquals(0, jvmOutput.exitCode);

    Path proguarded =
        File.createTempFile("proguarded", FileUtils.JAR_EXTENSION, tempRoot).toPath();
    ProcessResult proguardResult = ToolHelper.runProguardRaw(jarPath, proguarded, pgConfig, map);
    assertEquals(0, proguardResult.exitCode);

    AndroidApp processedApp = ToolHelper.runD8(readJar(proguarded));
    verifyFieldAccess(processedApp, jvmOutput);
  }

  @Test
  public void testR8() throws Exception {
    ProcessResult jvmOutput = ToolHelper.runJava(ImmutableList.of(jarPath), mainName);
    assertEquals(0, jvmOutput.exitCode);

    AndroidApp processedApp = runR8(originalApp);
    verifyFieldAccess(processedApp, jvmOutput);
  }

  private static InstructionSubject findInstructionOrNull(
      Iterator<InstructionSubject> iterator, Function<InstructionSubject, Boolean> predicate) {
    while (iterator.hasNext()) {
      InstructionSubject instruction = iterator.next();
      if (predicate.apply(instruction)) {
        return instruction;
      }
    }
    return null;
  }

  private void verifyFieldAccess(AndroidApp processedApp, ProcessResult jvmOutput)
      throws Exception {
    CodeInspector inspector = new CodeInspector(processedApp);
    ClassSubject impl = inspector.clazz(Impl.class);
    assertThat(impl, isPresent());
    MethodSubject init = impl.init(ImmutableList.of("java.lang.String"));
    assertThat(init, isPresent());
    Iterator<InstructionSubject> iterator = init.iterateInstructions();

    assertNotNull(findInstructionOrNull(iterator, InstructionSubject::isInvoke));

    InstructionSubject instruction =
        findInstructionOrNull(iterator, InstructionSubject::isInstancePut);
    assertNotNull(instruction);
    FieldAccessInstructionSubject fieldAccessInstruction =
        (FieldAccessInstructionSubject) instruction;
    assertEquals("name", fieldAccessInstruction.name());
    assertTrue(fieldAccessInstruction.holder().is(impl.getDexClass().type.toString()));

    assertNotNull(findInstructionOrNull(iterator, InstructionSubject::isReturnVoid));

    ProcessResult output;
    if (backend == Backend.DEX) {
      output = runOnArtRaw(processedApp, mainName);
    } else {
      assert backend == Backend.CF;
      output = runOnJavaRaw(processedApp, mainName, Collections.emptyList());
    }
    assertEquals(0, output.exitCode);
    assertEquals(jvmOutput.stdout, output.stdout);
  }

}
