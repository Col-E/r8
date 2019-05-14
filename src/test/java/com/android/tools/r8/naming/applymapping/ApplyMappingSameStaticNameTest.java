// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** This test reproduces b/131532229 on d8-1.4. */
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
    public static boolean[] a() {
      return null;
    }

    public static int c = 1;
  }

  public static class B extends A {
    public static boolean[] b() {
      return null;
    }

    public static int d = 2;
  }

  public static class C {}

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  private TestParameters parameters;

  public ApplyMappingSameStaticNameTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test_b131532229() throws CompilationFailedException {
    testForR8(parameters.getBackend())
        .noTreeShaking()
        .noMinification()
        .addLibraryClasses(A.class, B.class)
        .addLibraryFiles(runtimeJar(parameters))
        .addProgramClasses(C.class)
        .addApplyMapping(pgMap)
        .compile();
  }
}
