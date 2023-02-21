// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

@RunWith(Parameterized.class)
public class DebugLocalWithoutStackMapTypeTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public DebugLocalWithoutStackMapTypeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(MainDump.dump())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("addSuppressed");
  }

  public static class Main {

    private static final Method addSuppressedExceptionMethod;

    static {
      Method m;
      try {
        m = Throwable.class.getDeclaredMethod("addSuppressed", Throwable.class);
      } catch (Exception e) {
        m = null;
      }
      addSuppressedExceptionMethod = m;
    }

    public static void main(String[] args) {
      System.out.println(addSuppressedExceptionMethod.getName());
    }
  }

  // When compiling with JDK 7 the local variable for m is not split in Main::<clinit>. See
  // comment below where the local variable ranges for clinit is added.
  public static class MainDump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      FieldVisitor fieldVisitor;
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_PUBLIC | ACC_SUPER,
          "com/android/tools/r8/ir/DebugLocalWithoutStackMapTypeTest$Main",
          null,
          "java/lang/Object",
          null);

      classWriter.visitInnerClass(
          "com/android/tools/r8/ir/DebugLocalWithoutStackMapTypeTest$Main",
          "com/android/tools/r8/ir/DebugLocalWithoutStackMapTypeTest",
          "Main",
          ACC_PUBLIC | ACC_STATIC);

      {
        fieldVisitor =
            classWriter.visitField(
                ACC_PRIVATE | ACC_FINAL | ACC_STATIC,
                "addSuppressedExceptionMethod",
                "Ljava/lang/reflect/Method;",
                null,
                null);
        fieldVisitor.visitEnd();
      }
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
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(63, label0);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitFieldInsn(
            GETSTATIC,
            "com/android/tools/r8/ir/DebugLocalWithoutStackMapTypeTest$Main",
            "addSuppressedExceptionMethod",
            "Ljava/lang/reflect/Method;");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/lang/reflect/Method", "getName", "()Ljava/lang/String;", false);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(64, label1);
        methodVisitor.visitInsn(RETURN);
        Label label2 = new Label();
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLocalVariable("args", "[Ljava/lang/String;", null, label0, label2, 0);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        Label label1 = new Label();
        Label label2 = new Label();
        methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Exception");
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(55, label0);
        methodVisitor.visitLdcInsn(Type.getType("Ljava/lang/Throwable;"));
        methodVisitor.visitLdcInsn("addSuppressed");
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/Class");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitLdcInsn(Type.getType("Ljava/lang/Throwable;"));
        methodVisitor.visitInsn(AASTORE);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "java/lang/Class",
            "getDeclaredMethod",
            "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;",
            false);
        methodVisitor.visitVarInsn(ASTORE, 0);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(58, label1);
        Label label3 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label3);
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLineNumber(56, label2);
        methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Exception"});
        methodVisitor.visitVarInsn(ASTORE, 1);
        Label label4 = new Label();
        methodVisitor.visitLabel(label4);
        methodVisitor.visitLineNumber(57, label4);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitVarInsn(ASTORE, 0);
        methodVisitor.visitLabel(label3);
        methodVisitor.visitLineNumber(59, label3);
        methodVisitor.visitFrame(
            Opcodes.F_APPEND, 1, new Object[] {"java/lang/reflect/Method"}, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            PUTSTATIC,
            "com/android/tools/r8/ir/DebugLocalWithoutStackMapTypeTest$Main",
            "addSuppressedExceptionMethod",
            "Ljava/lang/reflect/Method;");
        Label label5 = new Label();
        methodVisitor.visitLabel(label5);
        methodVisitor.visitLineNumber(60, label5);
        methodVisitor.visitInsn(RETURN);
        // When compiling with JDK v7 the local variable for m is not split, and m therefore ranges
        // from label1 to label5. From JDK v8 and upward, the range for m is split in label1-label2
        // and label4-label5.
        methodVisitor.visitLocalVariable(
            "m", "Ljava/lang/reflect/Method;", null, label1, label5, 0);
        methodVisitor.visitLocalVariable("e", "Ljava/lang/Exception;", null, label4, label3, 1);
        methodVisitor.visitMaxs(6, 2);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
