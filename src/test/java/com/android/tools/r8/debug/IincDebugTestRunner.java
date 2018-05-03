// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.DebuggeeState;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.Test;

public class IincDebugTestRunner extends DebugTestBase {
  @Test
  public void compareDifferentRegister() throws Exception {
    compareOutput(IincDebugTestDump.dump(1, 2, false));
  }

  @Test
  public void compareLoadStoreSameRegister() throws Exception {
    compareOutput(IincDebugTestDump.dump(1, 1, false));
  }

  @Test
  public void compareIinc() throws Exception {
    compareOutput(IincDebugTestDump.dump(1, 1, true));
  }

  @Test
  public void stepDifferentRegister() throws Exception {
    stepOutput(IincDebugTestDump.dump(1, 2, false));
  }

  @Test
  public void stepLoadStoreSameRegister() throws Exception {
    stepOutput(IincDebugTestDump.dump(1, 1, false));
  }

  @Test
  public void stepIinc() throws Exception {
    stepOutput(IincDebugTestDump.dump(1, 1, true));
  }

  private void compareOutput(byte[] clazz) throws Exception {
    Path inputJar = buildInput(clazz);
    ProcessResult runInput = ToolHelper.runJava(inputJar, IincDebugTestDump.CLASS_NAME);
    assertEquals(0, runInput.exitCode);
    ProcessResult runCf = ToolHelper.runJava(buildCf(inputJar), IincDebugTestDump.CLASS_NAME);
    assertEquals(0, runCf.exitCode);
    assertEquals(runInput.toString(), runCf.toString());
    String runDex =
        ToolHelper.runArtNoVerificationErrors(
            buildDex(inputJar).toString(), IincDebugTestDump.CLASS_NAME);
    assertEquals(runInput.stdout, runDex);
  }

  private void stepOutput(byte[] clazz) throws Exception {
    // See verifyStateLocation in DebugTestBase.
    Assume.assumeTrue(
        "Streaming on Dalvik DEX runtimes has some unknown interference issue",
        ToolHelper.getDexVm().getVersion().isAtLeast(Version.V6_0_1));
    Assume.assumeTrue(
        "Skipping test "
            + testName.getMethodName()
            + " because debug tests are not yet supported on Windows",
        !ToolHelper.isWindows());
    Path inputJar = buildInput(clazz);
    new DebugStreamComparator()
        .add("Input", streamDebugTest(new CfDebugTestConfig(inputJar)))
        .add("R8/DEX", streamDebugTest(new DexDebugTestConfig(buildDex(inputJar))))
        .add("R8/CF", streamDebugTest(new CfDebugTestConfig(buildCf(inputJar))))
        .compare();
  }

  private Stream<DebuggeeState> streamDebugTest(DebugTestConfig config) throws Exception {
    return streamDebugTest(config, IincDebugTestDump.CLASS_NAME, ANDROID_FILTER);
  }

  private Path buildInput(byte[] clazz) {
    Path inputJar = temp.getRoot().toPath().resolve("input.jar");
    ArchiveConsumer inputJarConsumer = new ArchiveConsumer(inputJar);
    inputJarConsumer.accept(clazz, IincDebugTestDump.DESCRIPTOR, null);
    inputJarConsumer.finished(null);
    return inputJar;
  }

  private Path buildDex(Path inputJar) throws Exception {
    Path dexJar = temp.getRoot().toPath().resolve("r8dex.jar");
    build(inputJar, new DexIndexedConsumer.ArchiveConsumer(dexJar));
    return dexJar;
  }

  private Path buildCf(Path inputJar) throws Exception {
    Path cfJar = temp.getRoot().toPath().resolve("r8cf.jar");
    build(inputJar, new ArchiveConsumer(cfJar));
    return cfJar;
  }

  private void build(Path inputJar, ProgramConsumer consumer) throws Exception {
    Builder builder =
        R8Command.builder()
            .setMode(CompilationMode.DEBUG)
            .setProgramConsumer(consumer)
            .addProgramFiles(inputJar);
    if ((consumer instanceof ClassFileConsumer)) {
      builder.addLibraryFiles(Paths.get(ToolHelper.JAVA_8_RUNTIME));
    } else {
      builder.addLibraryFiles(ToolHelper.getAndroidJar(ToolHelper.getMinApiLevelForDexVm()));
    }
    // TODO(b/75997473): Enable inlining when supported by CF backend
    ToolHelper.runR8(
        builder.build(),
        options -> {
          options.enableInlining = false;
          options.invalidDebugInfoFatal = true;
        });
  }
}
