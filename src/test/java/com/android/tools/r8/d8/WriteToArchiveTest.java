// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.d8;

import com.android.tools.r8.CompilationException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.ToolHelper;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

// Test that we allow writing to zip and jar archives.
public class WriteToArchiveTest {

  private static final String input = ToolHelper.EXAMPLES_BUILD_DIR + "/trivial.jar";

  @Rule public TemporaryFolder zipFolder = ToolHelper.getTemporaryFolderForTest();
  @Rule public TemporaryFolder jarFolder = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void writeToZip() throws IOException, CompilationException {
    D8Command command =
        D8Command.builder()
            .addProgramFiles(Paths.get(input))
            .setOutputPath(Paths.get(zipFolder.getRoot().toString() + "/output.zip"))
            .build();
    D8.run(command);
  }

  @Test
  public void writeToJar() throws IOException, CompilationException {
    D8Command command =
        D8Command.builder()
            .addProgramFiles(Paths.get(input))
            .setOutputPath(Paths.get(jarFolder.getRoot().toString() + "/output.jar"))
            .build();
    D8.run(command);
  }
}
