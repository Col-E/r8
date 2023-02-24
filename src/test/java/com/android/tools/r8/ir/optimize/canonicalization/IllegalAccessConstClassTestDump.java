// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.canonicalization;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class IllegalAccessConstClassTestDump {

  // Originated from the following code snippet:
  //
  //   class ...PackagePrivateClass {}
  //
  // then repackaged to the top-level.
  static class PackagePrivateClassDump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(V1_8, ACC_SUPER, "PackagePrivateClass", null, "java/lang/Object", null);

      classWriter.visitSource("IllegalAccessConstClassTest.java", null);

      {
        methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(25, label0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(RETURN);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLocalVariable("this", "LPackagePrivateClass;", null, label0, label1, 0);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }

  // Originated from the following code snippet:
  //
  //   class FakePackagePrivateClassConsumer {
  //     public static void main(String... args) {
  //       if (System.currentTimeMillis() < -2) {
  //         System.out.println(PackagePrivateClass.class.getName());
  //       } else if (System.currentTimeMillis() < -1) {
  //         System.out.println(PackagePrivateClass.class.getSimpleName());
  //       } else {
  //         System.out.println("No need to load any classes");
  //       }
  //     }
  //   }
  //
  // then rewritten to use the repackaged PackagePrivateClass instead.
  static class FakePackagePrivateClassConsumerDump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_SUPER,
          "com/android/tools/r8/ir/optimize/canonicalization/FakePackagePrivateClassConsumer",
          null,
          "java/lang/Object",
          null);

      classWriter.visitSource("IllegalAccessConstClassTest.java", null);

      {
        methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(28, label0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(RETURN);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLocalVariable(
            "this",
            "Lcom/android/tools/r8/ir/optimize/canonicalization/FakePackagePrivateClassConsumer;",
            null,
            label0,
            label1,
            0);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(
            ACC_PUBLIC | ACC_STATIC | ACC_VARARGS, "main", "([Ljava/lang/String;)V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(30, label0);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
        methodVisitor.visitLdcInsn(new Long(-2L));
        methodVisitor.visitInsn(LCMP);
        Label label1 = new Label();
        methodVisitor.visitJumpInsn(IFGE, label1);
        Label label2 = new Label();
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLineNumber(31, label2);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn(Type.getType("LPackagePrivateClass;"));
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        Label label3 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label3);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(32, label1);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
        methodVisitor.visitLdcInsn(new Long(-1L));
        methodVisitor.visitInsn(LCMP);
        Label label4 = new Label();
        methodVisitor.visitJumpInsn(IFGE, label4);
        Label label5 = new Label();
        methodVisitor.visitLabel(label5);
        methodVisitor.visitLineNumber(33, label5);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn(Type.getType("LPackagePrivateClass;"));
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/lang/Class", "getSimpleName", "()Ljava/lang/String;", false);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        methodVisitor.visitJumpInsn(GOTO, label3);
        methodVisitor.visitLabel(label4);
        methodVisitor.visitLineNumber(35, label4);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("No need to load any classes");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        methodVisitor.visitLabel(label3);
        methodVisitor.visitLineNumber(37, label3);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitInsn(RETURN);
        Label label6 = new Label();
        methodVisitor.visitLabel(label6);
        methodVisitor.visitLocalVariable("args", "[Ljava/lang/String;", null, label0, label6, 0);
        methodVisitor.visitMaxs(4, 1);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
