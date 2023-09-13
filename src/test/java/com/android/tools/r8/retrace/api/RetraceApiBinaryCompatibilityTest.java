// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.TestDataSourceSet;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceApiBinaryCompatibilityTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RetraceApiBinaryCompatibilityTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testBinaryJarIsUpToDate() throws Exception {
    new RetraceApiTestCollection(temp).verifyCheckedInJarIsUpToDate();
  }

  @Test
  public void runCheckedInBinaryJar() throws Exception {
    new RetraceApiTestCollection(temp).runJunitOnCheckedInJar();
  }

  /**
   * To produce a new tests.jar run the code below. This will generate a new jar overwriting the
   * existing one. Remember to upload to cloud storage afterwards.
   */
  public static void main(String[] args) throws Exception {
    TemporaryFolder temp = new TemporaryFolder();
    temp.create();
    new RetraceApiTestCollection(temp)
        .replaceJarForCheckedInTestClasses(TestDataSourceSet.TESTS_JAVA_8);
  }
}
