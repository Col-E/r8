// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b150274427;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.MethodTransformer;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class JsrRetRegressionTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public JsrRetRegressionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testUnreachableJsrRet() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(getTransformClass(false))
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class);
  }

  @Test
  public void testReachableJsrRet() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addProgramClassFileData(getTransformClass(true))
          .run(parameters.getRuntime(), TestClass.class)
          .assertFailureWithErrorThatThrows(VerifyError.class);
      return;
    }
    try {
      testForD8()
          .addProgramClassFileData(getTransformClass(true))
          .setMinApi(parameters)
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                diagnostics.assertErrorMessageThatMatches(containsString("RET"));
              });
      fail();
    } catch (CompilationFailedException e) {
      // Expected error.
    }
  }

  private byte[] getTransformClass(boolean replaceThrow) throws IOException {
    return transformer(TestClass.class)
        .setVersion(50)
        .addMethodTransformer(
            new MethodTransformer() {
              @Override
              public void visitInsn(int opcode) {
                if (opcode == Opcodes.ATHROW) {
                  if (!replaceThrow) {
                    super.visitInsn(opcode);
                  }
                  super.visitVarInsn(Opcodes.RET, 0);
                } else {
                  super.visitInsn(opcode);
                }
              }
            })
        .transform();
  }

  private static class TestClass {

    public static void main(String[] args) {
      throw new RuntimeException();
      // Reachable or unreachable JSR inserted here.
    }
  }
}
