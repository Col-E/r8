// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class ClassWithAssertions {
  int x = 0;

  ClassWithAssertions(int x) {
    this.x = x;
  }

  boolean condition() {
    return x == 1;
  }

  int getX() {
    System.out.println("1");
    assert condition();
    System.out.println("2");
    return x;
  }

  public static void main(String[] args) {
    new ClassWithAssertions(Integer.parseInt(args[0])).getX();
  }
}

/* Below is an asmified dump of the above class */

class ClassWithAssertionsDump implements Opcodes {

  public static byte[] dump() {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;

    classWriter.visit(
        V1_8,
        ACC_PUBLIC | ACC_SUPER,
        "com/android/tools/r8/rewrite/assertions/ClassWithAssertions",
        null,
        "java/lang/Object",
        null);

    classWriter.visitSource("ClassWithAssertions.java", null);

    {
      fieldVisitor = classWriter.visitField(0, "x", "I", null, null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(
              ACC_FINAL | ACC_STATIC | ACC_SYNTHETIC, "$assertionsDisabled", "Z", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "<init>", "(I)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(10, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(8, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitFieldInsn(
          PUTFIELD, "com/android/tools/r8/rewrite/assertions/ClassWithAssertions", "x", "I");
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(11, label2);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitFieldInsn(
          PUTFIELD, "com/android/tools/r8/rewrite/assertions/ClassWithAssertions", "x", "I");
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(12, label3);
      methodVisitor.visitInsn(RETURN);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLocalVariable(
          "this",
          "Lcom/android/tools/r8/rewrite/assertions/ClassWithAssertions;",
          null,
          label0,
          label4,
          0);
      methodVisitor.visitLocalVariable("x", "I", null, label0, label4, 1);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "condition", "()Z", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(15, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(
          GETFIELD, "com/android/tools/r8/rewrite/assertions/ClassWithAssertions", "x", "I");
      methodVisitor.visitInsn(ICONST_1);
      Label label1 = new Label();
      methodVisitor.visitJumpInsn(IF_ICMPNE, label1);
      methodVisitor.visitInsn(ICONST_1);
      Label label2 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label2);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {Opcodes.INTEGER});
      methodVisitor.visitInsn(IRETURN);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLocalVariable(
          "this",
          "Lcom/android/tools/r8/rewrite/assertions/ClassWithAssertions;",
          null,
          label0,
          label3,
          0);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "getX", "()I", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(19, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("1");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(20, label1);
      methodVisitor.visitFieldInsn(
          GETSTATIC,
          "com/android/tools/r8/rewrite/assertions/ClassWithAssertions",
          "$assertionsDisabled",
          "Z");
      Label label2 = new Label();
      methodVisitor.visitJumpInsn(IFNE, label2);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "com/android/tools/r8/rewrite/assertions/ClassWithAssertions",
          "condition",
          "()Z",
          false);
      methodVisitor.visitJumpInsn(IFNE, label2);
      methodVisitor.visitTypeInsn(NEW, "java/lang/AssertionError");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "java/lang/AssertionError", "<init>", "()V", false);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(21, label2);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("2");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(22, label3);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(
          GETFIELD, "com/android/tools/r8/rewrite/assertions/ClassWithAssertions", "x", "I");
      methodVisitor.visitInsn(IRETURN);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLocalVariable(
          "this",
          "Lcom/android/tools/r8/rewrite/assertions/ClassWithAssertions;",
          null,
          label0,
          label4,
          0);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(26, label0);
      methodVisitor.visitTypeInsn(
          NEW, "com/android/tools/r8/rewrite/assertions/ClassWithAssertions");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitInsn(AALOAD);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "com/android/tools/r8/rewrite/assertions/ClassWithAssertions",
          "<init>",
          "(I)V",
          false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "com/android/tools/r8/rewrite/assertions/ClassWithAssertions",
          "getX",
          "()I",
          false);
      methodVisitor.visitInsn(POP);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(27, label1);
      methodVisitor.visitInsn(RETURN);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLocalVariable("args", "[Ljava/lang/String;", null, label0, label2, 0);
      methodVisitor.visitMaxs(4, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(7, label0);
      methodVisitor.visitLdcInsn(
          Type.getType("Lcom/android/tools/r8/rewrite/assertions/ClassWithAssertions;"));
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Class", "desiredAssertionStatus", "()Z", false);
      Label label1 = new Label();
      methodVisitor.visitJumpInsn(IFNE, label1);
      methodVisitor.visitInsn(ICONST_1);
      Label label2 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label2);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {Opcodes.INTEGER});
      methodVisitor.visitFieldInsn(
          PUTSTATIC,
          "com/android/tools/r8/rewrite/assertions/ClassWithAssertions",
          "$assertionsDisabled",
          "Z");
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 0);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
