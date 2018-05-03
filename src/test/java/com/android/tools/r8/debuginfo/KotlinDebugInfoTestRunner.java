// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class KotlinDebugInfoTestRunner extends TestBase {
  private Path buildInput(byte[] clazz, String descriptor) {
    Path inputJar = temp.getRoot().toPath().resolve("input.jar");
    ArchiveConsumer inputJarConsumer = new ArchiveConsumer(inputJar);
    inputJarConsumer.accept(clazz, descriptor, null);
    inputJarConsumer.finished(null);
    return inputJar;
  }

  private Path buildCf(Path inputJar) throws Exception {
    Path cfJar = temp.getRoot().toPath().resolve("r8cf.jar");
    build(inputJar, new ArchiveConsumer(cfJar));
    return cfJar;
  }

  @Test
  public void testRingBuffer() throws Exception {
    // This test hits the case where we simplify a DebugLocalWrite v'(x) <- v
    // with debug use [live: y], and y is written between v and v'.
    // In this case we must not move [live: y] to the definition of v,
    // since it causes the live range of y to extend to the entry to the first block.
    test(KotlinRingBufferDump.dump(), KotlinRingBufferDump.CLASS_NAME);
  }

  @Test
  public void testReflection() throws Exception {
    // This test hits the case where we replace a phi(v, v) that has local info
    // with v that has no local info.
    test(KotlinReflectionDump.dump(), KotlinReflectionDump.CLASS_NAME);
  }

  @Test
  public void testFoo() throws Exception {
    test(DebugInfoDump.dump(), DebugInfoDump.CLASS_NAME);
  }

  public void test(byte[] bytes, String className) throws Exception {
    String descriptor = 'L' + className.replace('.', '/') + ';';
    Path inputJar = buildInput(bytes, descriptor);
    ProcessResult runInput = ToolHelper.runJava(inputJar, className);
    if (0 != runInput.exitCode) {
      System.out.println(runInput);
    }
    assertEquals(0, runInput.exitCode);
    Path outCf = buildCf(inputJar);
    ProcessResult runCf = ToolHelper.runJava(outCf, className);
    assertEquals(runInput.toString(), runCf.toString());
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
