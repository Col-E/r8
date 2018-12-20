// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DescriptorUtils;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UninitializedInFrameTestRunner {
  static final Class CLASS = UninitializedInFrameTest.class;

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void test() throws Exception {
    test(ToolHelper.getClassAsBytes(CLASS));
  }

  @Test
  public void testDump() throws Exception {
    test(UninitializedInFrameDump.dump());
  }

  private void test(byte[] clazz) throws CompilationFailedException, IOException {
    Path input = temp.getRoot().toPath().resolve("input.jar");
    Path output = temp.getRoot().toPath().resolve("output.jar");

    ArchiveConsumer inputConsumer = new ArchiveConsumer(input);
    inputConsumer.accept(
        ByteDataView.of(clazz), DescriptorUtils.javaTypeToDescriptor(CLASS.getName()), null);
    inputConsumer.finished(null);
    ProcessResult runInput = ToolHelper.runJava(input, CLASS.getCanonicalName());
    if (runInput.exitCode != 0) {
      System.out.println(runInput);
    }
    Assert.assertEquals(0, runInput.exitCode);

    R8.run(
        R8Command.builder()
            .setMode(CompilationMode.DEBUG)
            .setDisableTreeShaking(true)
            .setDisableMinification(true)
            .addClassProgramData(clazz, Origin.unknown())
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setProgramConsumer(new ArchiveConsumer(output))
            .build());
    ProcessResult runOutput = ToolHelper.runJava(output, CLASS.getCanonicalName());
    if (runOutput.exitCode != 0) {
      System.out.println(runOutput);
    }
    Assert.assertEquals(runInput.toString(), runOutput.toString());
  }
}
