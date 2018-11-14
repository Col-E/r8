// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resource;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.TestDescriptionWatcher;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DataResourceTest {

  private TestBase.Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public DataResourceTest(TestBase.Backend backend) {
    this.backend = backend;
  }

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Rule
  public TestDescriptionWatcher watcher = new TestDescriptionWatcher();

  @Test
  public void dataResourceTest() throws IOException, CompilationFailedException {
    String packageName = "dataresource";
    String mainClassName = packageName + ".ResourceTest";
    Path inputJar = Paths.get(ToolHelper.EXAMPLES_BUILD_DIR,
        packageName + FileUtils.JAR_EXTENSION);

    ProcessResult referenceResult = ToolHelper.runJava(inputJar, mainClassName);

    assert backend == Backend.DEX || backend == Backend.CF;
    Path r8Out = temp.getRoot().toPath().resolve("r8out.jar");
    R8Command.Builder builder =
        R8Command.builder()
            .addProgramFiles(inputJar)
            .addProguardConfiguration(ImmutableList.of("-keepdirectories"), Origin.unknown())
            .setProgramConsumer(
                backend == Backend.DEX
                    ? new DexIndexedConsumer.ArchiveConsumer(r8Out, true)
                    : new ClassFileConsumer.ArchiveConsumer(r8Out, true));
    ToolHelper.runR8(builder.build());

    ProcessResult r8Result;
    if (backend == Backend.DEX) {
      r8Result = ToolHelper.runArtRaw(r8Out.toString(), mainClassName);
    } else {
      r8Result = ToolHelper.runJava(r8Out, mainClassName);
    }
    Assert.assertEquals(referenceResult.stdout, r8Result.stdout);
  }
}
