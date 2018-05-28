// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resource;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DataResourceTest {

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void dataResourceTest() throws IOException, CompilationFailedException {
    String packageName = "dataresource";
    String mainClassName = packageName + ".ResourceTest";
    Path inputJar = Paths.get(ToolHelper.EXAMPLES_BUILD_DIR,
        packageName + FileUtils.JAR_EXTENSION);

    ProcessResult referenceResult = ToolHelper.runJava(inputJar, mainClassName);

    Path r8Out = temp.getRoot().toPath().resolve("r8out.jar");
    R8Command.Builder builder =
        R8Command.builder()
            .addProgramFiles(inputJar)
            .setProgramConsumer(new DexIndexedConsumer.ArchiveConsumer(r8Out, true));
    ToolHelper.runR8(builder.build());

    ProcessResult r8Result = ToolHelper.runArtRaw(r8Out.toString(), mainClassName);
    Assert.assertEquals(referenceResult.stdout, r8Result.stdout);

  }
}
