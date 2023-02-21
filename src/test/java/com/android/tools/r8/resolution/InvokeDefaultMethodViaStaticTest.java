// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.resolution.invokestaticinterfacedefault.InterfaceDump;
import com.android.tools.r8.resolution.invokestaticinterfacedefault.MainDump;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeDefaultMethodViaStaticTest extends TestBase {

  private static final List<byte[]> CLASSES =
      ImmutableList.of(InterfaceDump.dump(), MainDump.dump());

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public InvokeDefaultMethodViaStaticTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private Class<? extends Throwable> getExpectedError() {
    return parameters.isDexRuntime()
            && parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V4_4_4)
        ? VerifyError.class
        : IncompatibleClassChangeError.class;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(CLASSES)
        .run(parameters.getRuntime(), "Main")
        .assertFailureWithErrorThatThrows(getExpectedError());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(CLASSES)
        .addKeepMainRule("Main")
        .setMinApi(parameters)
        .addOptionsModification(o -> o.testing.allowInvokeErrors = true)
        .run(parameters.getRuntime(), "Main")
        .assertFailureWithErrorThatThrows(getExpectedError());
  }
}
