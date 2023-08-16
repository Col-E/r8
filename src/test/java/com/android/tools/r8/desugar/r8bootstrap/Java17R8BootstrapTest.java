// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.r8bootstrap;

import static com.android.tools.r8.desugar.r8bootstrap.JavaBootstrapUtils.MAIN_KEEP;
import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cf.bootstrap.BootstrapCurrentEqualityTest;
import com.android.tools.r8.desugar.LibraryFilesHelper;
import com.android.tools.r8.examples.hello.HelloTestRunner;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This test relies on a freshly built build/libs/r8_with_relocated_deps_17.jar.
 *
 * <p>The test compiles Hello/R8 with the same settings using R8 compiled with Java 17 (non shrunk),
 * and the same but shrunk with/without nest desugaring. All generated jars should be run correctly
 * and identical.
 */
@RunWith(Parameterized.class)
public class Java17R8BootstrapTest extends TestBase {

  private static final Class<?> HELLO_CLASS = HelloTestRunner.getHelloClass();
  private static final String[] HELLO_KEEP = {
    "-keep class " + HELLO_CLASS.getTypeName() + " {  public static void main(...);}"
  };

  private static Path r8Lib17NoDesugar;
  private static Path r8Lib17Desugar;

  public Java17R8BootstrapTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimesStartingFromIncluding(CfVm.JDK17).build();
  }

  private static boolean supportsSealedClassesWhenGeneratingCf() {
    // TODO(b/227160052): When this is true enable this test.
    return false;
  }

  @BeforeClass
  public static void beforeAll() throws Exception {
    if (!supportsSealedClassesWhenGeneratingCf()) {
      return;
    }
    r8Lib17NoDesugar = compileR8(false);
    r8Lib17Desugar = compileR8(true);
  }

  private static Path compileR8(boolean desugar) throws Exception {
    return JavaBootstrapUtils.compileR8(
        ToolHelper.R8_WITH_RELOCATED_DEPS_17_JAR,
        LibraryFilesHelper.getJdk11LibraryFiles(getStaticTemp()),
        desugar);
  }

  private Path[] jarsToCompare() {
    return new Path[] {ToolHelper.R8_WITH_RELOCATED_DEPS_17_JAR, r8Lib17NoDesugar, r8Lib17Desugar};
  }

  @Test
  public void testHello() throws Exception {
    Assume.assumeTrue(!ToolHelper.isWindows());
    Assume.assumeTrue(JavaBootstrapUtils.exists(ToolHelper.R8_WITH_RELOCATED_DEPS_17_JAR));
    Assume.assumeTrue(supportsSealedClassesWhenGeneratingCf());
    Path prevGeneratedJar = null;
    String prevRunResult = null;
    Path helloJar = HelloTestRunner.writeHelloProgramJar(temp);
    for (Path jar : jarsToCompare()) {
      Path generatedJar =
          testForExternalR8(Backend.CF, parameters.getRuntime())
              .useProvidedR8(jar)
              .addProgramFiles(helloJar)
              .addKeepRules(HELLO_KEEP)
              .compile()
              .outputJar();
      String runResult =
          ToolHelper.runJava(
                  parameters.getRuntime().asCf(),
                  ImmutableList.of(generatedJar),
                  HELLO_CLASS.getTypeName())
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
    Assume.assumeTrue(CfVm.JDK17.lessThanOrEqual(parameters.getRuntime().asCf().getVm()));
    Assume.assumeTrue(JavaBootstrapUtils.exists(ToolHelper.R8_WITH_RELOCATED_DEPS_17_JAR));
    Assume.assumeTrue(supportsSealedClassesWhenGeneratingCf());
    Path prevGeneratedJar = null;
    Path helloJar = HelloTestRunner.writeHelloProgramJar(temp);
    for (Path jar : jarsToCompare()) {
      Path generatedJar =
          testForExternalR8(Backend.CF, parameters.getRuntime())
              .useProvidedR8(jar)
              .addProgramFiles(helloJar)
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
