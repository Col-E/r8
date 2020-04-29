// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;

@RunWith(Parameterized.class)
public class ConstantDynamicHolderTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withDexRuntimes()
        .withAllApiLevels()
        .build();
  }

  final TestParameters parameters;

  public ConstantDynamicHolderTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramClassFileData(getTransformedMain())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("null");
  }

  @Test(expected = CompilationFailedException.class)
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramClassFileData(getTransformedMain())
        .setMinApi(parameters.getApiLevel())
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertErrorMessageThatMatches(
                    containsString("Unsupported dynamic constant")));
  }

  @Test(expected = CompilationFailedException.class)
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getTransformedMain())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertErrorMessageThatMatches(
                    containsString("Unsupported dynamic constant")));
  }

  private byte[] getTransformedMain() throws IOException {
    return transformer(Main.class)
        .setMinVersion(CfVm.JDK11)
        .transformLdcInsnInMethod(
            "main",
            (value, continuation) -> {
              assertEquals("replaced by dynamic null constant", value);
              continuation.visitLdcInsn(getDynamicConstant());
            })
        .transform();
  }

  private ConstantDynamic getDynamicConstant() {
    return new ConstantDynamic(
        "dynamicnull",
        "Ljava/lang/String;",
        new Handle(
            H_INVOKESTATIC,
            "java/lang/invoke/ConstantBootstraps",
            "nullConstant",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)"
                + "Ljava/lang/Object;",
            false));
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println("replaced by dynamic null constant");
    }
  }
}
