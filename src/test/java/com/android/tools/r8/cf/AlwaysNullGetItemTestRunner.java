// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.TestDescriptionWatcher;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AlwaysNullGetItemTestRunner {
  static final Class CLASS = AlwaysNullGetItemTest.class;

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Rule
  public TestDescriptionWatcher watcher = new TestDescriptionWatcher();

  @Test
  public void test() throws Exception {
    ProcessResult runInput =
        ToolHelper.runJava(ToolHelper.getClassPathForTests(), CLASS.getCanonicalName());
    assertEquals(0, runInput.exitCode);
    Path outCf = temp.getRoot().toPath().resolve("cf.jar");
    Path outDex = temp.getRoot().toPath().resolve("dex.zip");
    R8.run(
        R8Command.builder()
            .setMode(CompilationMode.DEBUG)
            .setDisableTreeShaking(true)
            .setDisableMinification(true)
            .addClassProgramData(ToolHelper.getClassAsBytes(CLASS), Origin.unknown())
            .addLibraryFiles(ToolHelper.getAndroidJar(ToolHelper.getMinApiLevelForDexVm()))
            .setProgramConsumer(new DexIndexedConsumer.ArchiveConsumer(outDex))
            .build());
    R8.run(
        R8Command.builder()
            .setMode(CompilationMode.DEBUG)
            .setDisableTreeShaking(true)
            .setDisableMinification(true)
            .addClassProgramData(ToolHelper.getClassAsBytes(CLASS), Origin.unknown())
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setProgramConsumer(new ClassFileConsumer.ArchiveConsumer(outCf))
            .build());
    ProcessResult runCf = ToolHelper.runJava(outCf, CLASS.getCanonicalName());
    ProcessResult runDex = ToolHelper.runArtRaw(outDex.toString(), CLASS.getCanonicalName());
    assertEquals(runInput.toString(), runCf.toString());
    // Only compare stdout and exitCode since dex2oat prints to stderr.
    assertEquals(runInput.stdout, runDex.stdout);
    assertEquals(runInput.exitCode, runDex.exitCode);
  }

  @Test
  public void testNoCheckCast() throws Exception {
    // Test that JVM accepts javac output when method calls have been replaced by ACONST_NULL.
    Path out = temp.getRoot().toPath().resolve("aaload-null.jar");
    ClassFileConsumer.ArchiveConsumer archiveConsumer = new ClassFileConsumer.ArchiveConsumer(out);
    archiveConsumer.accept(
        ByteDataView.of(AlwaysNullGetItemDump.dump()),
        DescriptorUtils.javaTypeToDescriptor(CLASS.getCanonicalName()),
        null);
    archiveConsumer.finished(null);
    ProcessResult processResult = ToolHelper.runJava(out, CLASS.getCanonicalName());
    if (processResult.exitCode != 0) {
      System.out.println(processResult);
    }
    assertEquals(0, processResult.exitCode);
  }
}
