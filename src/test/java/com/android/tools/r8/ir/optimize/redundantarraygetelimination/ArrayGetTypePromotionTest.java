// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.redundantarraygetelimination;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/278573402. */
@RunWith(Parameterized.class)
public class ArrayGetTypePromotionTest extends TestBase {

  private static final String EXPECTED_OUTPUT = "Hello, world!";

  private static byte[] programClassFileData;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @BeforeClass
  public static void setup() throws IOException {
    programClassFileData =
        transformer(Main.class)
            .transformMethodInsnInMethod(
                "main",
                (opcode, owner, name, descriptor, isInterface, visitor) -> {
                  if (name.equals("getGreeter")) {
                    visitor.visitMethodInsn(
                        opcode, owner, "getGreeterAsObject", "()Ljava/lang/Object;", isInterface);
                  } else {
                    visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                  }
                })
            .transform();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(Greeter.class)
        .addProgramClassFileData(programClassFileData)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClasses(Greeter.class)
        .addProgramClassFileData(programClassFileData)
        .release()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/278573402): Disallow redundant array load elimination.
        .assertFailureWithErrorThatThrows(VerifyError.class);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Greeter.class)
        .addProgramClassFileData(programClassFileData)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/278573402): Disallow redundant array load elimination.
        .assertFailureWithErrorThatThrows(VerifyError.class);
  }

  static class Main {

    public static void main(String[] args) {
      Greeter[] greeter = new Greeter[1];
      greeter[0] = getGreeter(); // transformed to getGreeterAsObject
      greeter[0].greet();
    }

    // Replaced by getGreeterAsObject().
    @NeverInline
    static Greeter getGreeter() {
      throw new RuntimeException();
    }

    @NeverInline
    static Object getGreeterAsObject() {
      return System.currentTimeMillis() > 0 ? new Greeter() : new Object();
    }
  }

  static class Greeter {

    @NeverInline
    @NoMethodStaticizing
    void greet() {
      System.out.println("Hello, world!");
    }
  }
}
