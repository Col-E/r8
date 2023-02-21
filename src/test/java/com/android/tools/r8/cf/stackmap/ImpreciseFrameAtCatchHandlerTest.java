// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.stackmap;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.IntBox;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ImpreciseFrameAtCatchHandlerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(getTransformedMain())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Caught");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Caught");
  }

  private static byte[] getTransformedMain() throws IOException {
    IntBox witness = new IntBox();
    byte[] transformed =
        transformer(Main.class)
            .addMethodTransformer(
                new MethodTransformer() {

                  @Override
                  public void visitFrame(
                      int type, int numLocal, Object[] local, int numStack, Object[] stack) {
                    if (getContext().method.getMethodName().equals("main")
                        && numLocal == 0
                        && numStack == 1
                        && stack[0].equals(binaryName(Exception.class))) {
                      stack = new Object[] {binaryName(Object.class)};
                      witness.increment();
                    }
                    super.visitFrame(type, numLocal, local, numStack, stack);
                  }
                })
            .transform();
    assertEquals(1, witness.get());
    return transformed;
  }

  static class Main {

    public static void main(String[] args) {
      try {
        System.out.println(args[0]);
      } catch (Exception e) {
        System.out.println("Caught");
      }
    }
  }
}
