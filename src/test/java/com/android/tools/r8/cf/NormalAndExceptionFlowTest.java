// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class NormalAndExceptionFlowTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public NormalAndExceptionFlowTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    // This test documents that the Java C1 compiler will bail out when it finds an exception
    // handler that is also targeted by a normal control-flow jump. See b/296916426.
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addVmArguments("-XX:+PrintCompilation", "-Xcomp")
        .addProgramClassFileData(getTransformedTestClass())
        .run(parameters.getRuntime(), TestClass.class, "foo")
        .assertFailureWithErrorThatThrows(ArrayIndexOutOfBoundsException.class)
        .apply(
            r ->
                assertThat(
                    r.getStdOut(),
                    containsString(
                        "compilation bailout: "
                            + "Exception handler can be reached by both "
                            + "normal and exceptional control flow")));
  }

  private static byte[] getTransformedTestClass() throws IOException {
    return transformer(TestClass.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              Label handler1 = new Label();
              Label handler2 = new Label();
              Label exit = new Label();

              // Read an array index and pop result within a handler range.
              visitor.visitVarInsn(Opcodes.ALOAD, 0);
              visitor.visitLdcInsn(0);
              Label start1 = new Label();
              visitor.visitLabel(start1);
              visitor.visitInsn(Opcodes.AALOAD);
              Label end1 = new Label();
              visitor.visitLabel(end1);
              visitor.visitInsn(Opcodes.POP);

              // Read an array index and pop result within another handler range.
              visitor.visitVarInsn(Opcodes.ALOAD, 0);
              visitor.visitLdcInsn(1);
              Label start2 = new Label();
              visitor.visitLabel(start2);
              visitor.visitInsn(Opcodes.AALOAD);
              Label end2 = new Label();
              visitor.visitLabel(end2);
              visitor.visitInsn(Opcodes.POP);

              // Normal exit.
              visitor.visitJumpInsn(Opcodes.GOTO, exit);

              // First handler just jumps to the next handler leaving the exception on the stack.
              visitor.visitLabel(handler1);
              visitor.visitFrame(
                  Opcodes.F_FULL, 0, new Object[] {}, 1, new Object[] {"java/lang/Throwable"});
              visitor.visitJumpInsn(Opcodes.GOTO, handler2);

              // Second handler is either hit directly via exception table or jumped to above.
              visitor.visitLabel(handler2);
              visitor.visitFrame(
                  Opcodes.F_FULL, 0, new Object[] {}, 1, new Object[] {"java/lang/Throwable"});
              visitor.visitInsn(Opcodes.ATHROW);

              // Exit epilog - the method template inserts the "return" so it is not added here.
              visitor.visitLabel(exit);
              visitor.visitFrame(Opcodes.F_FULL, 0, new Object[] {}, 0, new Object[] {});

              visitor.visitTryCatchBlock(start1, end1, handler1, null);
              visitor.visitTryCatchBlock(start2, end2, handler2, null);
            })
        .setMaxs(MethodPredicate.onName("main"), 100, 100)
        .transform();
  }

  static class TestClass {

    public static void placeholder() {}

    public static void main(String[] args) {
      placeholder(); // replaced by transformation.
    }
  }
}
