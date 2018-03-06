// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ClassFileConsumer.DirectoryConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AnnotationTestRunner {
  static final Class CLASS = AnnotationTest.class;
  @Rule public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void test() throws Exception {
    ProcessResult runInput = ToolHelper
        .runJava(ToolHelper.getClassPathForTests(), CLASS.getCanonicalName());
    assertEquals(0, runInput.exitCode);
    Path out = temp.getRoot().toPath();
    R8.run(
        R8Command.builder()
            .setMode(CompilationMode.DEBUG)
            .addClassProgramData(ToolHelper.getClassAsBytes(CLASS), Origin.unknown())
            .addLibraryFiles(ToolHelper.getAndroidJar(ToolHelper.getMinApiLevelForDexVm()))
            .setProgramConsumer(new DirectoryConsumer(out))
            .build());
    ProcessResult runOutput = ToolHelper.runJava(out, CLASS.getCanonicalName());
    assertEquals(runInput.toString(), runOutput.toString());
  }
}
