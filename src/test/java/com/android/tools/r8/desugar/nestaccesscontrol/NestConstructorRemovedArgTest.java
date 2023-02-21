// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.classesOfNest;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.getExpectedResult;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.getMainClass;

import com.android.tools.r8.Jdk9TestUtils;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestConstructorRemovedArgTest extends TestBase {

  public NestConstructorRemovedArgTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withDexRuntime(DexVm.Version.first())
        .withDexRuntime(DexVm.Version.last())
        .withApiLevelsStartingAtIncluding(apiLevelWithInvokeCustomSupport())
        .enableApiLevelsForCf()
        .build();
  }

  @Test
  public void testRemoveArgConstructorNestsR8() throws Exception {
    parameters.assumeR8TestParameters();
    String nestID = "constructors";
    testForR8(parameters.getBackend())
        .addKeepMainRule(getMainClass(nestID))
        .addDontObfuscate()
        .setMinApi(parameters)
        .addOptionsModification(options -> options.enableClassInlining = false)
        .addProgramFiles(classesOfNest(nestID))
        .applyIf(parameters.isCfRuntime(), Jdk9TestUtils.addJdk9LibraryFiles(temp))
        .compile()
        .run(parameters.getRuntime(), getMainClass(nestID))
        .assertSuccessWithOutput(getExpectedResult(nestID));
  }

  @Test
  public void testRemoveArgConstructorNestsR8NoTreeShaking() throws Exception {
    parameters.assumeR8TestParameters();
    String nestID = "constructors";
    testForR8(parameters.getBackend())
        .noTreeShaking()
        .addKeepMainRule(getMainClass(nestID))
        .addDontObfuscate()
        .setMinApi(parameters)
        .addOptionsModification(options -> options.enableClassInlining = false)
        .addProgramFiles(classesOfNest(nestID))
        .applyIf(parameters.isCfRuntime(), Jdk9TestUtils.addJdk9LibraryFiles(temp))
        .compile()
        .run(parameters.getRuntime(), getMainClass(nestID))
        .assertSuccessWithOutput(getExpectedResult(nestID));
  }
}
