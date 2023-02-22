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
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApplyMappingRotateNameClashTest extends TestBase {

  public static class A {}

  public static class B {}

  public static class C {
    public static void main(String[] args) {
      System.out.print("HELLO WORLD!");
    }
  }

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test_b131532229() throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addLibraryClasses(A.class, B.class)
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .addProgramClasses(C.class)
        .addKeepMainRule(C.class)
        .noTreeShaking()
        .addApplyMapping(
            StringUtils.lines(
                A.class.getTypeName() + " -> " + B.class.getTypeName() + ":",
                B.class.getTypeName() + " -> " + A.class.getTypeName() + ":"))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), C.class)
        .assertSuccessWithOutput("HELLO WORLD!");
  }
}
