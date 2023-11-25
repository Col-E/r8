// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dagger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DaggerBasicNotSingletonUsingBindsTest extends DaggerBasicTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public String target;

  @Parameters(name = "{0}, javac -target {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimes()
            .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
            .withAllApiLevels()
            .build(),
        javacTargets);
  }

  @BeforeClass
  public static void setUp() throws Exception {
    compileWithoutSingleton();
  }

  public static final String MAIN_CLASS = "basic.MainUsingBinds";
  public static final List<String> EXPECTED_OUTPUT =
      ImmutableList.of("false", "false", "false", "I1Impl1", "I2Impl1", "I3Impl1");

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(target.equals(javacTargets.get(0)));
    testForJvm(parameters)
        .addProgramFiles(getProgramFiles(target))
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramFiles(getProgramFiles(target))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector) {
    assertEquals(1, inspector.allClasses().size());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(getProgramFiles(target))
        .setMinApi(parameters)
        .addKeepMainRule(MAIN_CLASS)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .inspect(this::inspect)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }
}
