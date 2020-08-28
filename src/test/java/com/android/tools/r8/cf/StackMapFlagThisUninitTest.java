// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class StackMapFlagThisUninitTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  public StackMapFlagThisUninitTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .addProgramClassFileData(ADump.dump())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(
            containsString(
                "Exception in thread \"main\" java.lang.VerifyError: Inconsistent stackmap frames"
                    + " at branch target 22"));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addProgramClassFileData(ADump.dump())
        .addKeepAllClassesRule()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/166738818): We should generate the correct stack map entry.
        .assertFailureWithErrorThatMatches(
            containsString(
                "Exception in thread \"main\" java.lang.VerifyError: Inconsistent stackmap frames"
                    + " at branch target 22"));
  }

  public static class Main {

    public static void main(String[] args) {
      new A(0);
    }
  }

  public static class A {

    public A(int i) {}
  }

  // TODO(b/166738818): This is the bytecode generated from the constructor merging in
  //  https://r8-review.googlesource.com/c/r8/+/53180/3.
  //  The important thing here is that the instantiation of this is not performed in one case
  //  and we fail to create a proper stack map entry for uninitializedThis.
  public static class ADump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_PUBLIC | ACC_SUPER,
          "com/android/tools/r8/cf/StackMapFlagThisUninitTest$A",
          null,
          "java/lang/Object",
          null);

      {
        methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "(I)V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ILOAD, 1);
        Label label0 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label0);
        methodVisitor.visitVarInsn(ILOAD, 1);
        methodVisitor.visitInsn(ICONST_1);
        Label label1 = new Label();
        methodVisitor.visitJumpInsn(IF_ICMPNE, label1);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("bar");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitFrame(Opcodes.F_CHOP, 2, null, 0, null);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitInsn(ATHROW);
        methodVisitor.visitLabel(label0);
        methodVisitor.visitFrame(
            Opcodes.F_APPEND, 1, new Object[] {Opcodes.UNINITIALIZED_THIS}, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("foo");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(2, 2);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
