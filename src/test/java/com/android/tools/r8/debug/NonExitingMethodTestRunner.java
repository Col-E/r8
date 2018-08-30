// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Assume;
import org.junit.Test;

public class NonExitingMethodTestRunner extends DebugTestBase {

  public static final Class CLASS = NonExitingMethodTest.class;
  public static final String FILE = CLASS.getSimpleName() + ".java";

  private static Path getClassFilePath() {
    return ToolHelper.getClassFileForTestClass(CLASS);
  }

  public DebugTestConfig cfConfig() {
    return new CfDebugTestConfig(ToolHelper.getClassPathForTests());
  }

  public DebugTestConfig d8Config() {
    return new D8DebugTestConfig().compileAndAdd(temp, getClassFilePath());
  }

  public DebugTestConfig r8CfConfig() throws CompilationFailedException, IOException {
    Path path = temp.getRoot().toPath().resolve("out.jar");
    ToolHelper.runR8(
        R8Command.builder()
            .setMode(CompilationMode.DEBUG)
            .addProgramFiles(getClassFilePath())
            .setProgramConsumer(new ClassFileConsumer.ArchiveConsumer(path))
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .build(),
        options -> options.enableCfFrontend = true);
    return new CfDebugTestConfig().addPaths(path);
  }

  @Test
  public void test() throws Exception {
    Assume.assumeTrue(
        "Skipping test "
            + testName.getMethodName()
            + " because debug tests are not yet supported on Windows",
        !ToolHelper.isWindows());
    new DebugStreamComparator()
        .add("CF", streamDebugTest(cfConfig(), CLASS.getCanonicalName(), NO_FILTER))
        .add("D8", streamDebugTest(d8Config(), CLASS.getCanonicalName(), NO_FILTER))
        .add("R8/CF", streamDebugTest(r8CfConfig(), CLASS.getCanonicalName(), NO_FILTER))
        .setFilter(s -> s.getSourceFile().equals(FILE))
        .compare();
  }
}
