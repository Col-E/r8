// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib.corelibjdktests;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Jdk11ObjectsTests extends TestBase {

  private static final Path JDK_11_OBJECTS_TESTS_DIR =
      Paths.get(ToolHelper.JAVA_CLASSES_DIR + "jdk11ObjectsTests");
  private static final String BASICOBJECTSTEST = "BasicObjectsTest";
  private static final String CLASS_SUFFIX = ".class";
  private static final Path[] JDK_11_OBJECTS_TEST_CLASS_FILES =
      new Path[] {
          JDK_11_OBJECTS_TESTS_DIR.resolve(BASICOBJECTSTEST + CLASS_SUFFIX),
      };

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        // TODO desugaring corelib is blocked by https://issuetracker.google.com/issues/114481425
        .withDexRuntimesStartingFromIncluding(DexVm.Version.V8_1_0)
        .withCfRuntime(CfVm.JDK11)
        .build();
  }

  public Jdk11ObjectsTests(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8Objects() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramFiles(JDK_11_OBJECTS_TEST_CLASS_FILES)
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), BASICOBJECTSTEST)
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
        .addKeepMainRule(BASICOBJECTSTEST)
        .addProgramFiles(JDK_11_OBJECTS_TEST_CLASS_FILES)
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), BASICOBJECTSTEST)
        .assertSuccessWithOutput("");
  }
}
