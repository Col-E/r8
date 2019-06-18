// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.methodparameters;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class MethodParametersTestDump implements Opcodes {

  /* The below dump was produced by the asmifier on following java code:

   import java.lang.reflect.Method;
   import java.lang.reflect.Parameter;

   public class MethodParametersTest {

     public static void main(String... hello) {
       for (Method method : MethodParametersTest.class.getMethods()) {
         for (Parameter parameter : method.getParameters()) {
           System.out.println(method.getName() + ": " + parameter.getName());
         }
       }
     }

     public void other(int darkness, String my, Object old, boolean friend) {

     }
   }
  */

  public static byte[] dump() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(
        V1_8,
        ACC_PUBLIC | ACC_SUPER,
        "com/android/tools/r8/naming/methodparameters/MethodParametersTest",
        null,
        "java/lang/Object",
        null);

    classWriter.visitSource("MethodParametersTest.java", null);

    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(4, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC | ACC_VARARGS, "main", "([Ljava/lang/String;)V", null, null);
      methodVisitor.visitParameter("hello", 0);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(7, label0);
      methodVisitor.visitLdcInsn(
          Type.getType("Lcom/android/tools/r8/naming/methodparameters/MethodParametersTest;"));
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Class", "getMethods", "()[Ljava/lang/reflect/Method;", false);
      methodVisitor.visitVarInsn(ASTORE, 1);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitInsn(ARRAYLENGTH);
      methodVisitor.visitVarInsn(ISTORE, 2);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitVarInsn(ISTORE, 3);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(
          Opcodes.F_APPEND,
          3,
          new Object[] {"[Ljava/lang/reflect/Method;", Opcodes.INTEGER, Opcodes.INTEGER},
          0,
          null);
      methodVisitor.visitVarInsn(ILOAD, 3);
      methodVisitor.visitVarInsn(ILOAD, 2);
      Label label2 = new Label();
      methodVisitor.visitJumpInsn(IF_ICMPGE, label2);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitVarInsn(ILOAD, 3);
      methodVisitor.visitInsn(AALOAD);
      methodVisitor.visitVarInsn(ASTORE, 4);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(8, label3);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/reflect/Method",
          "getParameters",
          "()[Ljava/lang/reflect/Parameter;",
          false);
      methodVisitor.visitVarInsn(ASTORE, 5);
      methodVisitor.visitVarInsn(ALOAD, 5);
      methodVisitor.visitInsn(ARRAYLENGTH);
      methodVisitor.visitVarInsn(ISTORE, 6);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitVarInsn(ISTORE, 7);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          8,
          new Object[] {
            "[Ljava/lang/String;",
            "[Ljava/lang/reflect/Method;",
            Opcodes.INTEGER,
            Opcodes.INTEGER,
            "java/lang/reflect/Method",
            "[Ljava/lang/reflect/Parameter;",
            Opcodes.INTEGER,
            Opcodes.INTEGER
          },
          0,
          new Object[] {});
      methodVisitor.visitVarInsn(ILOAD, 7);
      methodVisitor.visitVarInsn(ILOAD, 6);
      Label label5 = new Label();
      methodVisitor.visitJumpInsn(IF_ICMPGE, label5);
      methodVisitor.visitVarInsn(ALOAD, 5);
      methodVisitor.visitVarInsn(ILOAD, 7);
      methodVisitor.visitInsn(AALOAD);
      methodVisitor.visitVarInsn(ASTORE, 8);
      Label label6 = new Label();
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(9, label6);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/reflect/Method", "getName", "()Ljava/lang/String;", false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      methodVisitor.visitLdcInsn(": ");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      methodVisitor.visitVarInsn(ALOAD, 8);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/reflect/Parameter", "getName", "()Ljava/lang/String;", false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label7 = new Label();
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(8, label7);
      methodVisitor.visitIincInsn(7, 1);
      methodVisitor.visitJumpInsn(GOTO, label4);
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(7, label5);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          4,
          new Object[] {
            "[Ljava/lang/String;", "[Ljava/lang/reflect/Method;", Opcodes.INTEGER, Opcodes.INTEGER
          },
          0,
          new Object[] {});
      methodVisitor.visitIincInsn(3, 1);
      methodVisitor.visitJumpInsn(GOTO, label1);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(12, label2);
      methodVisitor.visitFrame(Opcodes.F_CHOP, 3, null, 0, null);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(3, 9);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC, "other", "(ILjava/lang/String;Ljava/lang/Object;Z)V", null, null);
      methodVisitor.visitParameter("darkness", 0);
      methodVisitor.visitParameter("my", 0);
      methodVisitor.visitParameter("old", 0);
      methodVisitor.visitParameter("friend", 0);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(16, label0);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(0, 5);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
