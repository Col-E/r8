// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * This test reproduces b/131532229 on d8-1.4. This test is basically just testing that we are not
 * throwing up on the mapping file that has static methods and fields that go to different names.
 */
@RunWith(Parameterized.class)
public class ApplyMappingSameStaticNameTest extends TestBase {

  public static final String pgMap =
      StringUtils.lines(
          A.class.getTypeName() + " -> " + A.class.getTypeName() + ":",
          "  boolean[] jacocoInit() -> a",
          "  int f1 -> c",
          B.class.getTypeName() + " -> " + B.class.getTypeName() + ":",
          "  boolean[] jacocoInit() -> b",
          "  int f1 -> d");

  public static class A {
    public static boolean[] jacocoInit() {
      return null;
    }

    public static int f1 = 1;
  }

  public static class B extends A {
    public static boolean[] jacocoInit() {
      return null;
    }

    public static int f1 = 2;
  }

  public static class C {}

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void test_b131532229() throws Exception {
    testForR8(parameters.getBackend())
        .noTreeShaking()
        .addLibraryClasses(A.class, B.class)
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .addProgramClasses(C.class)
        .addApplyMapping(pgMap)
        .compile();
  }
}
