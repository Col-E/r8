// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KotlinDebugInfoTestRunner extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  public KotlinDebugInfoTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  private Path buildInput(byte[] clazz, String descriptor) {
    Path inputJar = temp.getRoot().toPath().resolve("input.jar");
    ArchiveConsumer inputJarConsumer = new ArchiveConsumer(inputJar);
    inputJarConsumer.accept(ByteDataView.of(clazz), descriptor, null);
    inputJarConsumer.finished(null);
    return inputJar;
  }

  @Test
  public void testRingBuffer() throws Exception {
    // This test hits the case where we simplify a DebugLocalWrite v'(x) <- v
    // with debug use [live: y], and y is written between v and v'.
    // In this case we must not move [live: y] to the definition of v,
    // since it causes the live range of y to extend to the entry to the first block.
    test(
        KotlinRingBufferDump.dump(),
        KotlinRingBufferDump.CLASS_NAME,
        builder -> builder.addDontWarn("kotlin.Metadata"));
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

  public void test(byte[] bytes, String className) {}

  public void test(
      byte[] bytes, String className, ThrowableConsumer<R8FullTestBuilder> configuration)
      throws Exception {
    String descriptor = 'L' + className.replace('.', '/') + ';';
    Path inputJar = buildInput(bytes, descriptor);
    ProcessResult runInput = ToolHelper.runJava(inputJar, className);
    if (0 != runInput.exitCode) {
      System.out.println(runInput);
    }
    assertEquals(0, runInput.exitCode);

    testForR8(parameters.getBackend())
        .addProgramFiles(inputJar)
        .addKeepAttributeLineNumberTable()
        .addKeepAttributeSourceFile()
        .addOptionsModification(options -> options.invalidDebugInfoFatal = true)
        .apply(configuration)
        .debug()
        .addDontObfuscate()
        .noTreeShaking()
        .compile()
        .run(parameters.getRuntime(), className)
        .assertSuccessWithOutput(runInput.stdout);
  }
}
