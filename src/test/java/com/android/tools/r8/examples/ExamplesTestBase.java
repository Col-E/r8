// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.debug.CfDebugTestConfig;
import com.android.tools.r8.debug.DebugStreamComparator;
import com.android.tools.r8.debug.DebugTestBase;
import com.android.tools.r8.debug.DebugTestConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;

public abstract class ExamplesTestBase extends DebugTestBase {

  public final TestParameters parameters;

  public ExamplesTestBase(TestParameters parameters) {
    this.parameters = parameters;
  }

  public abstract String getExpected();

  public abstract Class<?> getMainClass();

  public List<Class<?>> getTestClasses() {
    return Collections.singletonList(getMainClass());
  }

  public void runTestDesugaring() throws Exception {
    testForDesugaring(parameters)
        .addProgramClasses(getTestClasses())
        .run(parameters.getRuntime(), getMainClass())
        .assertSuccessWithOutput(getExpected());
  }

  public void runTestR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addOptionsModification(o -> o.testing.roundtripThroughLir = true)
        .setMinApi(parameters)
        .addProgramClasses(getTestClasses())
        .addKeepMainRule(getMainClass())
        .run(parameters.getRuntime(), getMainClass())
        .assertSuccessWithOutput(getExpected());
  }

  public void runTestDebugComparator() throws Exception {
    Assume.assumeFalse(ToolHelper.isWindows());
    Assume.assumeFalse(
        "Debugging not enabled in 12.0.0", parameters.isDexRuntimeVersion(Version.V12_0_0));
    Assume.assumeTrue(
        "Streaming on Dalvik DEX runtimes has some unknown interference issue",
        parameters.isCfRuntime() || parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V6_0_1));

    String mainTypeName = getMainClass().getTypeName();
    DebugStreamComparator comparator =
        new DebugStreamComparator()
            .add("JVM", streamDebugTest(getJvmConfig(), mainTypeName, ANDROID_FILTER))
            .add("D8", streamDebugTest(getD8Config(), mainTypeName, ANDROID_FILTER))
            .setFilter(
                state -> state.getClassName().startsWith(getMainClass().getPackage().getName()));

    // Only add R8 on the representative config.
    if (parameters.isR8TestParameters()) {
      comparator.add("R8", streamDebugTest(getR8Config(), mainTypeName, ANDROID_FILTER));
    }

    comparator.compare();
  }

  private CfDebugTestConfig getJvmConfig() throws IOException {
    // We can't use `testForJvm` as we want to build the reference even for non-representative API.
    CfRuntime cfRuntime =
        parameters.isCfRuntime() ? parameters.asCfRuntime() : CfRuntime.getDefaultCfRuntime();
    Path jar = temp.newFolder().toPath().resolve("out.jar");
    writeClassesToJar(jar, getTestClasses());
    return new CfDebugTestConfig(cfRuntime, Collections.singletonList(jar));
  }

  private DebugTestConfig getD8Config() throws CompilationFailedException {
    return testForD8(parameters.getBackend())
        .addProgramClasses(getTestClasses())
        .setMinApi(parameters)
        .compile()
        .debugConfig(parameters.getRuntime());
  }

  private DebugTestConfig getR8Config() throws CompilationFailedException {
    return testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(getTestClasses())
        .addKeepMainRule(getMainClass())
        .debug()
        .compile()
        .debugConfig(parameters.getRuntime());
  }
}
