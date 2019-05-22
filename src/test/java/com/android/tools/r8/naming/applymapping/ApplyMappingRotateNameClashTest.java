// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ApplyMappingRotateNameClashTest extends TestBase {

  public static class A {}

  public static class B {}

  public static class C {
    public static void main(String[] args) {
      System.out.print("HELLO WORLD!");
    }
  }

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public ApplyMappingRotateNameClashTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test_b131532229() throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addLibraryClasses(A.class, B.class)
        .addLibraryFiles(TestBase.runtimeJar(parameters.getBackend()))
        .addProgramClasses(C.class)
        .addKeepMainRule(C.class)
        .noTreeShaking()
        .addApplyMapping(
            StringUtils.lines(
                A.class.getTypeName() + " -> " + B.class.getTypeName() + ":",
                B.class.getTypeName() + " -> " + A.class.getTypeName() + ":"))
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), C.class)
        .assertSuccessWithOutput("HELLO WORLD!");
  }
}
