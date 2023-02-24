// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.stackmap;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class SwitchStackFrameFallThroughTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public SwitchStackFrameFallThroughTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(SwitchStackFrameFallThroughTest$MainDump.dump())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("java.io.IOException");
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClassFileData(SwitchStackFrameFallThroughTest$MainDump.dump())
        .setMinApi(parameters)
        .addOptionsModification(options -> options.testing.readInputStackMaps = true)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("java.io.IOException");
  }

  public static class Main {

    public static void main(String[] args) {
      Throwable throwable = null;
      switch (args.length) {
        case 0:
          throwable = new IOException();
          break;
        case 1:
          throwable = new RuntimeException();
          break;
      }
      System.out.println(throwable.toString());
    }
  }

  // The above is roughly the code that below, except we move the switch targets above the switch
  // and then jump over them. We do this to check the fall-through of the switch.
  public static class SwitchStackFrameFallThroughTest$MainDump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_PUBLIC | ACC_SUPER,
          "com/android/tools/r8/cf/stackmap/SwitchStackFrameFallThroughTest$Main",
          null,
          "java/lang/Object",
          null);

      classWriter.visitSource("SwitchStackFrameFallThroughTest.java", null);

      classWriter.visitInnerClass(
          "com/android/tools/r8/cf/stackmap/SwitchStackFrameFallThroughTest$Main",
          "com/android/tools/r8/cf/stackmap/SwitchStackFrameFallThroughTest",
          "Main",
          ACC_PUBLIC | ACC_STATIC);

      {
        methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        methodVisitor.visitCode();
        // This is moved here without a jump.

        Label label0 = new Label();
        Label label1 = new Label();
        Label label2 = new Label();
        Label label3 = new Label();

        methodVisitor.visitJumpInsn(GOTO, label2);

        methodVisitor.visitLabel(label0);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            1,
            new Object[] {"[Ljava/lang/String;"},
            1,
            new Object[] {Opcodes.NULL});
        methodVisitor.visitInsn(POP);
        methodVisitor.visitTypeInsn(NEW, "java/io/IOException");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/io/IOException", "<init>", "()V", false);
        methodVisitor.visitVarInsn(ASTORE, 1);
        methodVisitor.visitJumpInsn(GOTO, label3);

        methodVisitor.visitLabel(label1);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            1,
            new Object[] {"[Ljava/lang/String;"},
            1,
            new Object[] {Opcodes.NULL});
        methodVisitor.visitInsn(POP);
        methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false);
        methodVisitor.visitVarInsn(ASTORE, 1);
        methodVisitor.visitJumpInsn(GOTO, label3);

        methodVisitor.visitLabel(label2);
        methodVisitor.visitFrame(Opcodes.F_FULL, 1, new Object[] {"[Ljava/lang/String;"}, 0, null);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitInsn(ARRAYLENGTH);
        methodVisitor.visitLookupSwitchInsn(label1, new int[] {0, 1}, new Label[] {label0, label1});
        methodVisitor.visitLabel(label3);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            2,
            new Object[] {"[Ljava/lang/String;", "java/lang/Throwable"},
            0,
            null);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/lang/Throwable", "toString", "()Ljava/lang/String;", false);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(4, 2);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
