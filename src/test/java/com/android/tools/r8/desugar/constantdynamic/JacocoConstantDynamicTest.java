// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.constantdynamic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.jacoco.JacocoClasses;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JacocoConstantDynamicTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean useConstantDynamic;

  @Parameters(name = "{0}, useConstantDynamic: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values());
  }

  public static JacocoClasses testClassesNoConstantDynamic;
  public static JacocoClasses testClassesConstantDynamic;

  public JacocoClasses testClasses;

  private static final String MAIN_CLASS = TestRunner.class.getTypeName();
  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world!", "Hello from I!");

  @BeforeClass
  public static void setUpInput() throws IOException {
    testClassesNoConstantDynamic = testClasses(getStaticTemp(), CfVersion.V1_8);
    testClassesConstantDynamic = testClasses(getStaticTemp(), CfVersion.V11);
  }

  @Before
  public void setUp() {
    testClasses = useConstantDynamic ? testClassesConstantDynamic : testClassesNoConstantDynamic;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(
        parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11) || !useConstantDynamic);

    // Run non-instrumented code.
    testForRuntime(parameters)
        .addProgramFiles(testClasses.getOriginal())
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);

    // Run non-instrumented code with an agent causing on the fly instrumentation on the JVM.
    Path output = temp.newFolder().toPath();
    Path agentOutputOnTheFly = output.resolve("on-the-fly");
    testForJvm(parameters)
        .addProgramFiles(testClasses.getOriginal())
        .enableJaCoCoAgent(ToolHelper.JACOCO_AGENT, agentOutputOnTheFly)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
    List<String> onTheFlyReport = testClasses.generateReport(agentOutputOnTheFly);
    assertEquals(3, onTheFlyReport.size());

    // Run the instrumented code.
    Path agentOutputOffline = output.resolve("offline");
    testForJvm(parameters)
        .addProgramFiles(testClasses.getInstrumented())
        .configureJaCoCoAgentForOfflineInstrumentedCode(ToolHelper.JACOCO_AGENT, agentOutputOffline)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
    List<String> offlineReport = testClasses.generateReport(agentOutputOffline);
    assertEquals(onTheFlyReport, offlineReport);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    if (!useConstantDynamic) {
      Path output = temp.newFolder().toPath();
      Path agentOutput = output.resolve("jacoco.exec");
      testForD8()
          .addProgramFiles(testClasses.getInstrumented())
          .addProgramFiles(ToolHelper.JACOCO_AGENT)
          .setMinApi(parameters)
          .compile()
          .runWithJaCoCo(agentOutput, parameters.getRuntime(), MAIN_CLASS)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
      // TODO(sgjesse): Need to figure out why there is no instrumentation output for newer VMs.
      if (parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_HOST)) {
        List<String> report = testClasses.generateReport(agentOutput);
        assertEquals(3, report.size());
      } else {
        assertFalse(Files.exists(agentOutput));
      }
    } else {
      testForD8()
          .addProgramFiles(testClasses.getInstrumented())
          .addProgramFiles(ToolHelper.JACOCO_AGENT)
          .setMinApi(parameters)
          .compile();
    }
  }

  private static JacocoClasses testClasses(TemporaryFolder temp, CfVersion version)
      throws IOException {
    return new JacocoClasses(
        ImmutableList.of(
            transformer(TestRunner.class).setVersion(version).transform(),
            transformer(I.class).setVersion(version).transform()),
        temp);
  }

  interface I {
    default void m() {
      System.out.println("Hello from I!");
    }
  }

  static class TestRunner implements I {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
      new TestRunner().m();
    }
  }
}
