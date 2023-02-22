// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;

import com.android.tools.r8.Jdk9TestUtils;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Jdk11ObjectsTests extends TestBase {

  private static final String BASIC_OBJECTS_TEST = "BasicObjectsTest";
  private static Path[] JDK_11_OBJECTS_TEST_CLASS_FILES;
  private static final Path JDK_11_OBJECTS_JAVA_DIR =
      Paths.get(ToolHelper.JDK_11_TESTS_DIR + "java/util/Objects");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        // TODO desugaring desugaredlibrary is blocked by
        // https://issuetracker.google.com/issues/114481425
        .withDexRuntimesStartingFromIncluding(DexVm.Version.V8_1_0)
        .withMaximumApiLevel()
        .withCfRuntime(CfVm.JDK11)
        .build();
  }

  @BeforeClass
  public static void compileObjectsClass() throws Exception {
    Path jdk11ObjectsTestsDir = getStaticTemp().newFolder("objects").toPath();
    javac(TestRuntime.getCheckedInJdk11(), getStaticTemp())
        .addSourceFiles(JDK_11_OBJECTS_JAVA_DIR.resolve(BASIC_OBJECTS_TEST + JAVA_EXTENSION))
        .setOutputPath(jdk11ObjectsTestsDir)
        .compile();
    JDK_11_OBJECTS_TEST_CLASS_FILES =
        new Path[] {
          jdk11ObjectsTestsDir.resolve(BASIC_OBJECTS_TEST + CLASS_EXTENSION),
        };
  }

  @Test
  public void testD8Objects() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramFiles(JDK_11_OBJECTS_TEST_CLASS_FILES)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), BASIC_OBJECTS_TEST)
        .assertSuccessWithOutput("");
  }

  @Test
  public void testR8Objects() throws Exception {
    Path libraryJar;
    if (parameters.isDexRuntime()) {
      libraryJar = ToolHelper.getAndroidJar(parameters.getRuntime().asDex().getMinApiLevel());
    } else {
      libraryJar = ToolHelper.getJava8RuntimeJar();
    }
    testForR8(parameters.getBackend())
        .addLibraryFiles(libraryJar)
        .addKeepMainRule(BASIC_OBJECTS_TEST)
        .addProgramFiles(JDK_11_OBJECTS_TEST_CLASS_FILES)
        .applyIf(parameters.isCfRuntime(), Jdk9TestUtils.addJdk9LibraryFiles(temp))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), BASIC_OBJECTS_TEST)
        .assertSuccessWithOutput("");
  }
}
