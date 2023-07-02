// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.constantdynamic;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.jacoco.JacocoClasses;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JacocoConstantDynamicGetDeclaredMethods extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public static final String jacocoBootstrapMethodName = "$jacocoInit";

  public static JacocoClasses testClasses;

  private static final String MAIN_CLASS = TestRunner.class.getTypeName();
  private static final String EXPECTED_OUTPUT_WITH_METHOD_HANDLES =
      StringUtils.lines(
          jacocoBootstrapMethodName,
          "3",
          "java.lang.invoke.MethodHandles$Lookup",
          "java.lang.String",
          "java.lang.Class");
  private static final String EXPECTED_OUTPUT_WITHOUT_PARAMETERS =
      StringUtils.lines(jacocoBootstrapMethodName, "0");
  private static final String EXPECTED_OUTPUT_WITHOUT_METHOD_HANDLES =
      StringUtils.lines(
          jacocoBootstrapMethodName,
          "3",
          "java.lang.Object",
          "java.lang.String",
          "java.lang.Class");

  @BeforeClass
  public static void setUpInput() throws IOException {
    testClasses = testClasses(getStaticTemp());
  }

  private void checkJacocoReport(Path agentOutput) throws IOException {
    // TODO(sgjesse): Need to figure out why there is no instrumentation output for newer VMs.
    if (parameters.isCfRuntime()
        || parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_HOST)) {
      assertEquals(2, testClasses.generateReport(agentOutput).size());
    } else {
      assertFalse(Files.exists(agentOutput));
    }
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11));

    // Run non-instrumented code with an agent causing on the fly instrumentation on the JVM.
    Path output = temp.newFolder().toPath();
    Path agentOutputOnTheFly = output.resolve("on-the-fly");
    testForJvm(parameters)
        .addProgramFiles(testClasses.getOriginal())
        .enableJaCoCoAgent(ToolHelper.JACOCO_AGENT, agentOutputOnTheFly)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT_WITH_METHOD_HANDLES);
    checkJacocoReport(agentOutputOnTheFly);

    // Run the instrumented code.
    Path agentOutputOffline = output.resolve("offline");
    testForJvm(parameters)
        .addProgramFiles(testClasses.getInstrumented())
        .configureJaCoCoAgentForOfflineInstrumentedCode(ToolHelper.JACOCO_AGENT, agentOutputOffline)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT_WITH_METHOD_HANDLES);
    checkJacocoReport(agentOutputOffline);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.getRuntime().isDex());
    Path agentOutput = temp.newFolder().toPath().resolve("jacoco.exec");
    testForD8(parameters.getBackend())
        .addProgramFiles(testClasses.getInstrumented())
        .addProgramFiles(ToolHelper.JACOCO_AGENT)
        .setMinApi(parameters)
        .compile()
        .runWithJaCoCo(agentOutput, parameters.getRuntime(), MAIN_CLASS)
        .applyIf(
            parameters.getApiLevel().isLessThan(AndroidApiLevel.O),
            b -> b.assertSuccessWithOutput(EXPECTED_OUTPUT_WITHOUT_METHOD_HANDLES),
            b -> b.assertSuccessWithOutput(EXPECTED_OUTPUT_WITH_METHOD_HANDLES));
    checkJacocoReport(agentOutput);
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(parameters.getRuntime().isDex());
    Path agentOutput = temp.newFolder().toPath().resolve("jacoco.exec");
    testForR8(parameters.getBackend())
        .addProgramFiles(testClasses.getInstrumented())
        .addProgramFiles(ToolHelper.JACOCO_AGENT)
        .setMinApi(parameters)
        .addKeepMainRules(TestRunner.class)
        .addKeepRules(
            "-keepclassmembers,allowoptimization,allowshrinking class " + MAIN_CLASS + " {",
            "  boolean[] " + jacocoBootstrapMethodName + "(...);",
            "}")
        .applyIf(
            parameters.getApiLevel().isLessThan(AndroidApiLevel.O),
            b -> b.addDontWarn(MethodHandles.Lookup.class))
        .addDontWarn("java.lang.management.ManagementFactory", "javax.management.**")
        .compile()
        .runWithJaCoCo(agentOutput, parameters.getRuntime(), MAIN_CLASS)
        .inspect(
            inspector -> {
              MethodSubject jacocoBootstrapMethodSubject =
                  inspector
                      .clazz(TestRunner.class)
                      .uniqueMethodWithOriginalName(jacocoBootstrapMethodName);
              assertThat(jacocoBootstrapMethodSubject, isPresent());
              assertEquals(0, jacocoBootstrapMethodSubject.getParameters().size());
            })
        .assertSuccessWithOutput(EXPECTED_OUTPUT_WITHOUT_PARAMETERS);
    checkJacocoReport(agentOutput);
  }

  @Test
  public void testR8KeepingJacocoInit() throws Exception {
    assumeTrue(parameters.getRuntime().isDex());
    Path agentOutput = temp.newFolder().toPath().resolve("jacoco.exec");
    testForR8(parameters.getBackend())
        .addProgramFiles(testClasses.getInstrumented())
        .addProgramFiles(ToolHelper.JACOCO_AGENT)
        .setMinApi(parameters)
        .addKeepMainRules(TestRunner.class)
        .addKeepRules("-keep class ** { *** " + jacocoBootstrapMethodName + "(...); }")
        .applyIf(
            parameters.getApiLevel().isLessThan(AndroidApiLevel.O),
            b -> b.addDontWarn(MethodHandles.Lookup.class))
        .addDontWarn(
            "java.lang.instrument.ClassFileTransformer",
            "java.lang.management.ManagementFactory",
            "javax.management.**")
        .compile()
        .runWithJaCoCo(agentOutput, parameters.getRuntime(), MAIN_CLASS)
        .applyIf(
            parameters.getDexRuntimeVersion().isOlderThan(Version.V8_1_0),
            b -> b.assertFailureWithErrorThatThrows(ClassNotFoundException.class),
            b -> b.assertSuccessWithOutput(EXPECTED_OUTPUT_WITH_METHOD_HANDLES));
    checkJacocoReport(agentOutput);
  }

  private static JacocoClasses testClasses(TemporaryFolder temp) throws IOException {
    return new JacocoClasses(
        transformer(TestRunner.class).setVersion(CfVersion.V11).transform(), temp);
  }

  static class TestRunner {

    public static void main(String[] args) {
      Method[] methods = TestRunner.class.getDeclaredMethods();
      for (Method method : methods) {
        if (method.getName().equals(jacocoBootstrapMethodName)) {
          System.out.println(method.getName());
          System.out.println(method.getParameterTypes().length);
          for (int j = 0; j < method.getParameterTypes().length; j++) {
            System.out.println(method.getParameterTypes()[j].getName());
          }
          return;
        }
      }
      System.out.println("No " + jacocoBootstrapMethodName + " method");
    }
  }
}
