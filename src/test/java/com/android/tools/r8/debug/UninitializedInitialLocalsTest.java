// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class UninitializedInitialLocalsTest extends TestBase implements Opcodes {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(dump())
        .run(parameters.getRuntime(), "Test")
        .assertSuccessWithOutputLines("42");
  }

  @Test
  public void test() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClassFileData(dump())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), "Test")
        .assertSuccessWithOutputLines("42");
  }

  public static byte[] dump() {
    ClassWriter cw = new ClassWriter(0);
    cw.visit(V1_6, ACC_PUBLIC | ACC_SUPER, "Test", null, "java/lang/Object", null);
    cw.visitSource("Test.java", null);
    Label label0 = new Label();
    Label label1 = new Label();
    Label label2 = new Label();
    MethodVisitor mv =
        cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
    mv.visitCode();
    mv.visitLabel(label0);
    mv.visitLdcInsn(42);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitInsn(ARRAYLENGTH);
    mv.visitJumpInsn(IFEQ, label1);
    mv.visitInsn(DUP);
    mv.visitVarInsn(ISTORE, 1);
    mv.visitLabel(label1); // Here index 1 will be a phi of an undefined and real value.
    printInt(mv);
    println(mv);
    mv.visitInsn(RETURN);
    mv.visitLabel(label2);
    mv.visitMaxs(10, 20);
    mv.visitLocalVariable("args", "[Ljava/lang/String;", null, label0, label2, 0);
    mv.visitLocalVariable("invalid", "I", null, label0, label2, 1);
    mv.visitEnd();
    cw.visitEnd();
    return cw.toByteArray();
  }

  private static void printInt(MethodVisitor mv) {
    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    mv.visitInsn(SWAP);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(I)V", false);
  }

  private static void println(MethodVisitor mv) {
    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "()V", false);
  }
}
