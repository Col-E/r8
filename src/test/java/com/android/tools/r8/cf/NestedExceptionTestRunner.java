// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidAppConsumers;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class NestedExceptionTestRunner {
  static final Class CLASS = NestedExceptionTest.class;
  @Rule public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void testNestedException() throws Exception {
    AndroidAppConsumers a = new AndroidAppConsumers();
    R8.run(
        R8Command.builder()
            .setMode(CompilationMode.DEBUG)
            .addClassProgramData(ToolHelper.getClassAsBytes(CLASS), Origin.unknown())
            .addLibraryFiles(ToolHelper.getAndroidJar(ToolHelper.getMinApiLevelForDexVm()))
            .setProgramConsumer(a.wrapClassFileConsumer(null))
            .build());
    Path out = temp.newFolder().toPath();
    a.build().writeToDirectory(out, OutputMode.ClassFile);
    ToolHelper.runJava(out, CLASS.getCanonicalName());
  }
}
