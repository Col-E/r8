// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.DebuggeeState;
import com.android.tools.r8.utils.FileUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.Test;

public class ExamplesDebugTest extends DebugTestBase {

  private Path getExampleJar(String pkg) {
    return Paths.get(
        ToolHelper.EXAMPLES_BUILD_DIR, pkg + "_debuginfo_all" + FileUtils.JAR_EXTENSION);
  }

  public DebugTestConfig cfConfig(String pkg) throws Exception {
    return new CfDebugTestConfig(getExampleJar(pkg));
  }

  public DebugTestConfig d8Config(String pkg) throws Exception {
    return new D8DebugTestConfig().compileAndAdd(temp, getExampleJar(pkg));
  }

  public DebugTestConfig r8DebugCfConfig(String pkg) throws Exception {
    Path input = getExampleJar(pkg);
    Path output = temp.newFolder().toPath().resolve("r8_debug_cf_output.jar");
    ToolHelper.runR8(
        R8Command.builder()
            .setOutputPath(output)
            .addProgramFiles(input)
            .setMode(CompilationMode.DEBUG)
            .build(),
        options -> options.outputClassFiles = true);
    return new CfDebugTestConfig(output);
  }

  @Test
  public void testArithmetic() throws Throwable {
    String pkg = "arithmetic";
    String clazzName = pkg + ".Arithmetic";
    Stream<DebuggeeState> cf = streamDebugTest(cfConfig("arithmetic"), clazzName, ANDROID_FILTER);
    Stream<DebuggeeState> d8 = streamDebugTest(d8Config("arithmetic"), clazzName, ANDROID_FILTER);
    Stream<DebuggeeState> r8 =
        streamDebugTest(r8DebugCfConfig("arithmetic"), clazzName, ANDROID_FILTER);
    new DebugStreamComparator()
        .add("CF", cf)
        .add("D8", d8)
        .add("R8/CF", r8)
        // TODO(zerny): Implement debug locals info in the CF backend.
        .setVerifyVariables(false)
        .compare();
  }
}
