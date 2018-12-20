// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.DescriptorUtils;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;

public class PhiDefinitionsTestRunner extends TestBase {

  private ProcessResult runInput;
  private String className = PhiDefinitionsTest.class.getName();

  private Path writeAndRunOriginal() throws IOException {
    Path originalJar = temp.getRoot().toPath().resolve("originput.jar");
    ClassFileConsumer consumer = new ClassFileConsumer.ArchiveConsumer(originalJar);
    for (Class clazz : PhiDefinitionsTest.CLASSES) {
      String descriptor = DescriptorUtils.javaTypeToDescriptor(clazz.getName());
      consumer.accept(ByteDataView.of(ToolHelper.getClassAsBytes(clazz)), descriptor, null);
    }
    consumer.finished(null);
    runOriginalJar(originalJar);
    return originalJar;
  }

  private void runOriginalJar(Path originalJar) throws IOException {
    runInput = ToolHelper.runJava(originalJar, className);
    if (runInput.exitCode != 0) {
      System.out.println(runInput);
    }
    assertEquals(0, runInput.exitCode);
  }

  private Path writeAndRunInputJar() throws Exception {
    Path originalJar = writeAndRunOriginal();
    Path inputJar = temp.getRoot().toPath().resolve("input.jar");
    build(originalJar, new ClassFileConsumer.ArchiveConsumer(inputJar));
    runCf(inputJar, className);
    return inputJar;
  }

  private Path writeAndRunDumpJar() throws Exception {
    Path dumpJar = temp.getRoot().toPath().resolve("dump.jar");
    ClassFileConsumer consumer = new ClassFileConsumer.ArchiveConsumer(dumpJar);
    String desc = 'L' + PhiDefinitionsTestDump.INTERNAL_NAME + ';';
    consumer.accept(ByteDataView.of(PhiDefinitionsTestDump.dump()), desc, null);
    String innerDesc = 'L' + PhiDefinitionsTestDump.INNER_INTERNAL_NAME + ';';
    consumer.accept(ByteDataView.of(PhiDefinitionsTestDump.dumpInner()), innerDesc, null);
    consumer.finished(null);
    runOriginalJar(dumpJar);
    return dumpJar;
  }

  @Test
  public void testCf() throws Exception {
    Path outCf = temp.getRoot().toPath().resolve("cf.zip");
    build(writeAndRunInputJar(), new ClassFileConsumer.ArchiveConsumer(outCf));
    runCf(outCf, className);
  }

  @Test
  public void testDex() throws Exception {
    Path outDex = temp.getRoot().toPath().resolve("dex.zip");
    build(writeAndRunInputJar(), new DexIndexedConsumer.ArchiveConsumer(outDex));
    runDex(outDex, className);
  }

  @Test
  public void testCfDump() throws Exception {
    Path outCf = temp.getRoot().toPath().resolve("dump-cf.zip");
    build(writeAndRunDumpJar(), new ClassFileConsumer.ArchiveConsumer(outCf));
    runCf(outCf, className);
  }

  @Test
  public void testDexDump() throws Exception {
    Path outDex = temp.getRoot().toPath().resolve("dump-dex.zip");
    build(writeAndRunDumpJar(), new DexIndexedConsumer.ArchiveConsumer(outDex));
    runDex(outDex, className);
  }

  private void runCf(Path outCf, String className) throws Exception {
    ProcessResult runCf = ToolHelper.runJava(outCf, className);
    assertEquals(runInput.toString(), runCf.toString());
  }

  private void runDex(Path outDex, String className) throws Exception {
    ProcessResult runDex = ToolHelper.runArtNoVerificationErrorsRaw(outDex.toString(), className);
    assertEquals(runInput.stdout, runDex.stdout);
    assertEquals(runInput.exitCode, runDex.exitCode);
  }

  private void build(Path inputJar, ProgramConsumer consumer) throws Exception {
    Builder builder =
        R8Command.builder()
            .setMode(CompilationMode.DEBUG)
            .setDisableTreeShaking(true)
            .setDisableMinification(true)
            .setProgramConsumer(consumer)
            .addProgramFiles(inputJar);
    if (consumer instanceof ClassFileConsumer) {
      builder.addLibraryFiles(ToolHelper.getJava8RuntimeJar());
    } else {
      builder.addLibraryFiles(ToolHelper.getAndroidJar(ToolHelper.getMinApiLevelForDexVm()));
    }
    ToolHelper.runR8(builder.build(), options -> options.invalidDebugInfoFatal = true);
  }
}
