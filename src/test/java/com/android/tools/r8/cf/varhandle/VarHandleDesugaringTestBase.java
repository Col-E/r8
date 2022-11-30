// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.varhandle;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.examples.jdk9.VarHandle;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ZipUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class VarHandleDesugaringTestBase extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .withDexRuntimes()
        .withAllApiLevels()
        .build();
  }

  protected abstract String getMainClass();

  protected String getKeepRules() {
    return "";
  }

  protected abstract String getJarEntry();

  protected abstract String getExpectedOutput();

  @Test
  public void testReference() throws Throwable {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramFiles(VarHandle.jar())
        .run(parameters.getRuntime(), getMainClass())
        .assertSuccessWithOutput(getExpectedOutput());
  }

  @Test
  public void testD8() throws Throwable {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        // Use android.jar from Android T to get the VarHandle type. This is not strictly needed
        // to D8 as it does not fail on missing types.
        // TODO(b/247076137): With desugaring removing VarHandle the type should not be needed in
        //  the library and any android.jar should work.
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .addProgramClassFileData(ZipUtils.readSingleEntry(VarHandle.jar(), getJarEntry()))
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), getMainClass())
        // TODO(b/247076137): Test should pass on all platforms with desugaring implemented.
        .applyIf(
            // VarHandle is available from Android 9, even though it was not a public API until 13.
            parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V7_0_0),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class),
            parameters.getApiLevel().isLessThan(AndroidApiLevel.P)
                || parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V8_1_0),
            r -> r.assertFailure(),
            r -> r.assertSuccessWithOutput(getExpectedOutput()));
  }

  @Test
  public void testR8() throws Throwable {
    testForR8(parameters.getBackend())
        // Use android.jar from Android T to get the VarHandle type.
        // TODO(b/247076137): With desugaring removing VarHandle the type should not be needed in
        //  the library and any android.jar should work.
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .addProgramClassFileData(ZipUtils.readSingleEntry(VarHandle.jar(), getJarEntry()))
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(getMainClass())
        .addKeepRules(getKeepRules())
        .applyIf(
            parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(AndroidApiLevel.O),
            R8TestBuilder::allowDiagnosticWarningMessages)
        .run(parameters.getRuntime(), getMainClass())
        .applyIf(
            // VarHandle is available from Android 9, even though it was not a public API until 13.
            parameters.isDexRuntime()
                && parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V7_0_0),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class),
            parameters.isDexRuntime()
                && (parameters.getApiLevel().isLessThan(AndroidApiLevel.P)
                    || parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V8_1_0)),
            r -> r.assertFailure(),
            r -> r.assertSuccessWithOutput(getExpectedOutput()));
  }
}
