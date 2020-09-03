// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
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

  @Test
  public void testReference() throws Exception {
    TestRunResult<?> result =
        testForRuntime(parameters)
            .addProgramClassFileData(CLASSES)
            .run(parameters.getRuntime(), "Main");
    if (parameters.isDexRuntime()
        && parameters.getApiLevel().isLessThan(apiLevelWithDefaultInterfaceMethodsSupport())) {
      // TODO(b/167535447): Desugaring should preserve the error.
      result.assertFailureWithErrorThatThrows(NoSuchMethodError.class);
    } else {
      result.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
    }
  }

  @Test
  public void testR8() throws Exception {
    TestRunResult<?> result =
        testForR8(parameters.getBackend())
            .addProgramClassFileData(CLASSES)
            .addKeepMainRule("Main")
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(o -> o.testing.allowInvokeErrors = true)
            .run(parameters.getRuntime(), "Main");
    if (parameters.isDexRuntime()
        && parameters.getApiLevel().isLessThan(apiLevelWithDefaultInterfaceMethodsSupport())) {
      // TODO(b/167535447): Desugaring should preserve the error.
      result.assertFailureWithErrorThatThrows(NoClassDefFoundError.class);
    } else {
      result.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
    }
  }
}
