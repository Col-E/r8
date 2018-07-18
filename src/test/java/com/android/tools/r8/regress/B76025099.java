// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.code.InvokeDirect;
import com.android.tools.r8.code.IputObject;
import com.android.tools.r8.code.ReturnVoid;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import regress_76025099.Main;
import regress_76025099.impl.Impl;

@RunWith(VmTestRunner.class)
public class B76025099 extends TestBase {
  private static final String PRG =
      ToolHelper.EXAMPLES_BUILD_DIR + "regress_76025099" + FileUtils.JAR_EXTENSION;

  private AndroidApp runR8(AndroidApp app) throws Exception {
     R8Command command =
        ToolHelper.addProguardConfigurationConsumer(
            ToolHelper.prepareR8CommandBuilder(app),
            pgConfig -> {
              pgConfig.setPrintMapping(true);
              pgConfig.setPrintMappingFile(map);
            })
        .addProguardConfigurationFiles(pgConfig)
        .setOutput(tempRoot.toPath(), OutputMode.DexIndexed)
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

  private void verifyFieldAccess(AndroidApp processedApp, ProcessResult jvmOutput)
      throws Exception {
    CodeInspector inspector = new CodeInspector(processedApp);
    ClassSubject impl = inspector.clazz(Impl.class);
    assertThat(impl, isPresent());
    MethodSubject init = impl.init(ImmutableList.of("java.lang.String"));
    assertThat(init, isPresent());
    DexCode dexCode = init.getMethod().getCode().asDexCode();
    checkInstructions(
        dexCode, ImmutableList.of(InvokeDirect.class, IputObject.class, ReturnVoid.class));
    IputObject iput = (IputObject) dexCode.instructions[1];
    DexField fld = iput.getField();
    assertEquals("name", fld.name.toString());
    assertEquals(impl.getDexClass().type, fld.getHolder());

    ProcessResult artOutput = runOnArtRaw(processedApp, mainName);
    assertEquals(0, artOutput.exitCode);
    assertEquals(jvmOutput.stdout, artOutput.stdout);
  }

}
