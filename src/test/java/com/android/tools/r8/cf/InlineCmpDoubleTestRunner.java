// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidAppConsumers;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class InlineCmpDoubleTestRunner {
  static final Class CLASS = InlineCmpDoubleTest.class;
  @Rule public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void test() throws Exception {
    byte[] inputClass = ToolHelper.getClassAsBytes(CLASS);
    AndroidAppConsumers appBuilder = new AndroidAppConsumers();
    Path outPath = temp.getRoot().toPath().resolve("out.jar");
    R8Command command =  R8Command.builder()
            .setMode(CompilationMode.RELEASE)
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setProgramConsumer(appBuilder.wrapClassFileConsumer(new ArchiveConsumer(outPath)))
            .addClassProgramData(inputClass, Origin.unknown())
            .build();

    ToolHelper.runR8(command, options -> {
          options.enableCfFrontend = true;
          options.enableInlining = true;
          options.inliningInstructionLimit = 40;
        });

    assert ToolHelper.runJava(outPath, CLASS.getCanonicalName()).exitCode == 0;
  }
}
