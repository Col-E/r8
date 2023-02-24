// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.constantdynamic;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsLevel;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.errors.ConstantDynamicDesugarDiagnostic;
import com.android.tools.r8.errors.UnsupportedConstDynamicDiagnostic;
import com.android.tools.r8.errors.UnsupportedFeatureDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;

@RunWith(Parameterized.class)
public class ConstantDynamicHolderTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    assumeTrue(parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK11));
    assumeTrue(parameters.getApiLevel().isEqualTo(AndroidApiLevel.B));

    testForJvm(parameters)
        .addProgramClassFileData(getTransformedMain())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("null");
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();

    testForD8()
        .addProgramClassFileData(getTransformedMain())
        .setMinApi(parameters)
        .setDiagnosticsLevelModifier(
            (level, diagnostic) ->
                (diagnostic instanceof UnsupportedFeatureDiagnostic
                        || diagnostic instanceof ConstantDynamicDesugarDiagnostic)
                    ? DiagnosticsLevel.WARNING
                    : level)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertWarningsMatch(
                    diagnosticType(UnsupportedConstDynamicDiagnostic.class),
                    allOf(
                        diagnosticType(ConstantDynamicDesugarDiagnostic.class),
                        diagnosticMessage(
                            containsString(
                                "Unsupported dynamic constant (runtime provided bootstrap"
                                    + " method)")))))
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .assertFailureWithErrorThatMatches(containsString("const-dynamic"));
  }

  // TODO(b/198142625): Support const-dynamic in IR CF/CF.
  @Test(expected = CompilationFailedException.class)
  public void testR8Cf() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK11));
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getTransformedMain())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .compile();
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(parameters.isDexRuntime());

    testForR8(parameters.getBackend())
        .addProgramClassFileData(getTransformedMain())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .allowDiagnosticWarningMessages()
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              if (parameters.isDexRuntime()) {
                diagnostics.assertWarningsMatch(
                    allOf(
                        diagnosticType(UnsupportedFeatureDiagnostic.class),
                        diagnosticMessage(containsString("const-dynamic"))));
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .assertFailureWithErrorThatMatches(containsString("const-dynamic"));
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
