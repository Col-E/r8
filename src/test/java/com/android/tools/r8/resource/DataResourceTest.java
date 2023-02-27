// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resource;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.TestDescriptionWatcher;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DataResourceTest extends TestBase {

  private static final String PACKAGE_NAME = "dataresource";
  private static final String MAIN_CLASS_NAME = PACKAGE_NAME + ".ResourceTest";
  private static final Path INPUT_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, PACKAGE_NAME + FileUtils.JAR_EXTENSION);
  private static final String EXPECTED_OUTPUT =
      StringUtils.unixLines(
          "LibClass dir: true",
          "LibClass properties: true",
          "LibClass property: com.test.lib.LibClass",
          "LibClass text: this is a text with some content",
          "- partly matching pattern 123dataresource.lib.LibClass123",
          "- totally matching pattern dataresource.lib.LibClass and something after",
          "- matching the package dataresource.lib",
          "- matching class simple name LibClass",
          "- or only single element of the package name: lib",
          "- matching class descriptor dataresource/lib/LibClass",
          "- matching class full descriptor Ldataresource/lib/LibClass;",
          "- matching windows path dataresource\\lib\\LibClass",
          "- matching pattern dataresource.lib.LibClass.",
          "- matching pattern .dataresource.lib.LibClass",
          "- matching pattern dataresource.lib.LibClass,",
          "- matching pattern ,dataresource.lib.LibClass",
          "- matching pattern =dataresource.lib.LibClass",
          "- matching pattern dataresource.lib.LibClass=",
          "- matching pattern dataresource.lib.LibClass/",
          "- matching pattern /dataresource.lib.LibClass",
          "- matching pattern ?dataresource.lib.LibClass",
          "- matching pattern dataresource.lib.LibClass?",
          "- matching pattern dataresource.lib.LibClass!",
          "- matching pattern !dataresource.lib.LibClass",
          "- matching pattern :dataresource.lib.LibClass",
          "- matching pattern dataresource.lib.LibClass:",
          "- matching pattern dataresource.lib.LibClass*",
          "- matching pattern *dataresource.lib.LibClass",
          "- matching pattern $dataresource.lib.LibClass",
          "- matching pattern +dataresource.lib.LibClass",
          "- matching pattern -dataresource.lib.LibClass",
          "- matching pattern ^dataresource.lib.LibClass",
          "- matching pattern @dataresource.lib.LibClass",
          "- matching pattern (dataresource.lib.LibClass",
          "- matching pattern )dataresource.lib.LibClass",
          "- matching pattern àdataresource.lib.LibClass",
          "- matching pattern |dataresource.lib.LibClass",
          "- matching pattern [dataresource.lib.LibClass",
          "- matching pattern 'dataresource.lib.LibClass",
          "- matching pattern \"dataresource.lib.LibClass",
          "- matching pattern `dataresource.lib.LibClass",
          "- matching pattern ~dataresource.lib.LibClass",
          "- matching pattern &dataresource.lib.LibClass",
          "- matching pattern -dataresource.lib.LibClass",
          "- matching pattern dataresource.lib.LibClass-",
          "",
          "LibClass const string: dataresource.lib.LibClass",
          "LibClass concat string: dataresource.lib.LibClasscom.test.lib.LibClass",
          "LibClass field: dataresource.lib.LibClass");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Rule
  public TestDescriptionWatcher watcher = new TestDescriptionWatcher();

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramFiles(INPUT_JAR)
        .run(parameters.getRuntime(), MAIN_CLASS_NAME)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void dataResourceTest() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(INPUT_JAR)
        .addKeepRules("-keepdirectories")
        .addDontObfuscate()
        .addDontShrink()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), MAIN_CLASS_NAME)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }
}
