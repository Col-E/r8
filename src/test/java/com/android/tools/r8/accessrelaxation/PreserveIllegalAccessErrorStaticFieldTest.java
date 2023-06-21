// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.transformers.ClassFileTransformer.FieldPredicate;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PreserveIllegalAccessErrorStaticFieldTest extends TestBase {

  private static byte[] programClassFileData;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @BeforeClass
  public static void setup() throws Exception {
    programClassFileData =
        transformer(A.class)
            .setAccessFlags(FieldPredicate.onName("f"), FieldAccessFlags::setPrivate)
            .transform();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .addProgramClassFileData(programClassFileData)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addProgramClassFileData(programClassFileData)
        .addKeepMainRule(Main.class)
        .allowAccessModification()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  static class Main {

    public static void main(String[] args) {
      int x = A.f;
    }
  }

  static class A {

    /*private*/ static int f;
  }
}
