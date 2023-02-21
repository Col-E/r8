// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.resolution.shadowing1.AClassDump;
import com.android.tools.r8.resolution.shadowing1.InterfaceDump;
import com.android.tools.r8.resolution.shadowing1.MainDump;
import com.android.tools.r8.resolution.shadowing1.SubInterfaceDump;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DefaultMethodShadowedByStaticTest extends TestBase {

  private final List<byte[]> CLASSES =
      ImmutableList.of(
          InterfaceDump.dump(), SubInterfaceDump.dump(), AClassDump.dump(), MainDump.dump());

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public DefaultMethodShadowedByStaticTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(CLASSES)
        .run(parameters.getRuntime(), "Main")
        .applyIf(
            // When not desugaring interfaces, the v7 runtime fails to throw the correct error.
            parameters.canUseDefaultAndStaticInterfaceMethods()
                && parameters.isDexRuntimeVersion(Version.V7_0_0),
            r -> r.assertSuccessWithOutputLines("42"),
            r -> r.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(CLASSES)
        .addKeepMainRule("Main")
        .setMinApi(parameters)
        .run(parameters.getRuntime(), "Main")
        .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
  }
}
