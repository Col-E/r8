// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.KeepingDiagnosticHandler;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinStdLibCompilationTest extends TestBase {

  private static final Path kotlinStdLib =
      Paths.get("third_party", "kotlin", "kotlinc", "lib", "kotlin-stdlib.jar");

  private final boolean useCfFrontend;

  @Parameters(name = "UseCf={0}")
  public static Object[] setup() {
    return new Object[] {true, false};
  }

  public KotlinStdLibCompilationTest(boolean useCfFrontend) {
    this.useCfFrontend = useCfFrontend;
  }

  @Test
  public void testD8() throws CompilationFailedException {
    Assume.assumeFalse("b/110804489 New CF frontend fails on type conflict.", useCfFrontend);
    KeepingDiagnosticHandler handler = new KeepingDiagnosticHandler();
    ToolHelper.runD8(
        D8Command.builder(handler)
            .setMode(CompilationMode.DEBUG)
            .addProgramFiles(kotlinStdLib)
            .addLibraryFiles(ToolHelper.getAndroidJar(ToolHelper.getMinApiLevelForDexVm()))
            .setProgramConsumer(DexIndexedConsumer.emptyConsumer()),
        internalOptions -> internalOptions.enableCfFrontend = useCfFrontend);
    assertTrue(
        "Unexpected diagnostics: " + getMessages(handler),
        handler.infos.stream().noneMatch(d -> d.getDiagnosticMessage().contains("invalid locals")));
  }

  @Test
  public void testR8Dex() throws CompilationFailedException {
    Assume.assumeFalse("b/117969690 New CF frontend does not support DEX output.", useCfFrontend);
    testR8(Backend.DEX);
  }

  @Test
  public void testR8Cf() throws CompilationFailedException {
    testR8(Backend.CF);
  }

  public void testR8(Backend backend) throws CompilationFailedException {
    Assume.assumeFalse("b/110804489 New CF frontend fails on type conflict.", useCfFrontend);
    KeepingDiagnosticHandler handler = new KeepingDiagnosticHandler();
    ToolHelper.runR8(
        R8Command.builder(handler)
            .setMode(CompilationMode.DEBUG)
            .addProgramFiles(kotlinStdLib)
            .addLibraryFiles(runtimeJar(backend))
            .setProgramConsumer(emptyConsumer(backend))
            .build(),
        internalOptions -> internalOptions.enableCfFrontend = useCfFrontend);
    assertTrue(
        "Unexpected diagnostics: " + getMessages(handler),
        handler.infos.stream().noneMatch(d -> d.getDiagnosticMessage().contains("invalid locals")));
  }

  private String getMessages(KeepingDiagnosticHandler handler) {
    return String.join(
        "\n",
        handler.infos.stream().map(d -> d.getDiagnosticMessage()).collect(Collectors.toList()));
  }
}
