// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Test for some dup_x bytecodes that no existing test actually generate. */
@RunWith(Parameterized.class)
public class InstructionsTest extends TestBase implements Opcodes {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public InstructionsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addProgramClassFileData(dump())
          .run(parameters.getRuntime(), "Test")
          .assertSuccessWithOutputLines(EXPECTED);
    }
    testForR8(parameters.getBackend())
        .addProgramClassFileData(dump())
        .addKeepMainRule("Test")
        .setMinApi(parameters)
        .run(parameters.getRuntime(), "Test")
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static String[] EXPECTED = new String[] {"12312", "73.427", "3.0213.0", "321.032"};

  public static byte[] dump() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(V1_6, ACC_PUBLIC | ACC_SUPER, "Test", null, "java/lang/Object", null);

    classWriter.visitSource("Test.java", null);

    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      // Test for dup2x1 with ...,single,single,single
      methodVisitor.visitLdcInsn(3);
      methodVisitor.visitLdcInsn(2);
      methodVisitor.visitLdcInsn(1);
      methodVisitor.visitInsn(DUP2_X1);
      printInt(methodVisitor);
      printInt(methodVisitor);
      printInt(methodVisitor);
      printInt(methodVisitor);
      printInt(methodVisitor);
      println(methodVisitor);
      // Test for dup1x2 with wide,single,...
      methodVisitor.visitLdcInsn((double) 3.42);
      methodVisitor.visitLdcInsn(7);
      methodVisitor.visitInsn(DUP_X2);
      printInt(methodVisitor);
      printDouble(methodVisitor);
      printInt(methodVisitor);
      println(methodVisitor);
      // Test for dup2x2 with wide,single,single,...
      methodVisitor.visitLdcInsn(1);
      methodVisitor.visitLdcInsn(2);
      methodVisitor.visitLdcInsn((double) 3.0);
      methodVisitor.visitInsn(DUP2_X2);
      printDouble(methodVisitor);
      printInt(methodVisitor);
      printInt(methodVisitor);
      printDouble(methodVisitor);
      println(methodVisitor);
      // Test for dup2x2 with single,single,wide,...
      methodVisitor.visitLdcInsn((double) 1.0);
      methodVisitor.visitLdcInsn(2);
      methodVisitor.visitLdcInsn(3);
      methodVisitor.visitInsn(DUP2_X2);
      printInt(methodVisitor);
      printInt(methodVisitor);
      printDouble(methodVisitor);
      printInt(methodVisitor);
      printInt(methodVisitor);
      println(methodVisitor);
      // Tests done.
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(10, 20);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();
    return classWriter.toByteArray();
  }

  private static void printInt(MethodVisitor methodVisitor) {
    methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    methodVisitor.visitInsn(SWAP);
    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(I)V", false);
  }

  private static void printDouble(MethodVisitor methodVisitor) {
    methodVisitor.visitVarInsn(DSTORE, 1);
    methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    methodVisitor.visitVarInsn(DLOAD, 1);
    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(D)V", false);
  }

  private static void println(MethodVisitor methodVisitor) {
    methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "()V", false);
  }
}
