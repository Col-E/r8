// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.r8bootstrap;

import static com.android.tools.r8.desugar.r8bootstrap.JavaBootstrapUtils.MAIN_KEEP;
import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.Jdk11TestUtils;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cf.bootstrap.BootstrapCurrentEqualityTest;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This test relies on a freshly built build/libs/r8_with_relocated_deps_11.jar.
 *
 * <p>The test compiles Hello/R8 with the same settings using R8 compiled with Java 11 (non shrunk),
 * and the same but shrunk with/without nest desugaring. All generated jars should be run correctly
 * and identical.
 */
@RunWith(Parameterized.class)
public class Java11R8BootstrapTest extends TestBase {

  private static final String[] HELLO_KEEP = {
    "-keep class hello.Hello {  public static void main(...);}"
  };

  private static Path r8Lib11NoDesugar;
  private static Path r8Lib11Desugar;

  public Java11R8BootstrapTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimesStartingFromIncluding(CfVm.JDK11).build();
  }

  @BeforeClass
  public static void beforeAll() throws Exception {
    r8Lib11NoDesugar = compileR8(false);
    r8Lib11Desugar = compileR8(true);
  }

  private static Path compileR8(boolean desugar) throws Exception {
    return JavaBootstrapUtils.compileR8(
        ToolHelper.R8_WITH_RELOCATED_DEPS_11_JAR,
        Jdk11TestUtils.getJdk11LibraryFiles(getStaticTemp()),
        desugar);
  }

  private Path[] jarsToCompare() {
    return new Path[] {
      ToolHelper.R8_WITH_RELOCATED_DEPS_11_JAR,
      r8Lib11NoDesugar,
      r8Lib11Desugar
    };
  }

  @Test
  public void testHello() throws Exception {
    Assume.assumeTrue(!ToolHelper.isWindows());
    Path prevGeneratedJar = null;
    String prevRunResult = null;
    for (Path jar : jarsToCompare()) {
      Path generatedJar =
          testForExternalR8(Backend.CF, parameters.getRuntime())
              .useProvidedR8(jar)
              .addProgramFiles(Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "hello" + JAR_EXTENSION))
              .addKeepRules(HELLO_KEEP)
              .compile()
              .outputJar();
      String runResult =
          ToolHelper.runJava(
                  parameters.getRuntime().asCf(), ImmutableList.of(generatedJar), "hello.Hello")
              .toString();
      if (prevRunResult != null) {
        assertEquals(prevRunResult, runResult);
      }
      prevRunResult = runResult;
      if (prevGeneratedJar != null) {
        uploadJarsToCloudStorageIfTestFails(
            BootstrapCurrentEqualityTest::assertProgramsEqual, prevGeneratedJar, generatedJar);
      }
      prevGeneratedJar = generatedJar;
    }
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue(!ToolHelper.isWindows());
    Assume.assumeTrue(parameters.isCfRuntime());
    Assume.assumeTrue(CfVm.JDK11.lessThanOrEqual(parameters.getRuntime().asCf().getVm()));
    Path prevGeneratedJar = null;
    for (Path jar : jarsToCompare()) {
      Path generatedJar =
          testForExternalR8(Backend.CF, parameters.getRuntime())
              .useProvidedR8(jar)
              .addProgramFiles(Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "hello" + JAR_EXTENSION))
              .addKeepRuleFiles(MAIN_KEEP)
              .compile()
              .outputJar();
      if (prevGeneratedJar != null) {
        uploadJarsToCloudStorageIfTestFails(
            BootstrapCurrentEqualityTest::assertProgramsEqual, prevGeneratedJar, generatedJar);
      }
      prevGeneratedJar = generatedJar;
    }
  }
}
