// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.d8;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class TestADump implements Opcodes {

  public static byte[] dump() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_8, ACC_SUPER,
        "com/android/tools/r8/d8/TestA",
        null,
        "java/lang/Object",
        null);

    classWriter.visitSource("DuplicateAnnotationTest.java", null);

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
          "this", "Lcom/android/tools/r8/d8/TestA;", null, label0, label1, 0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "foo", "()V", null, null);
      {
        annotationVisitor0 =
            methodVisitor.visitAnnotation("Lcom/android/tools/r8/d8/TestKeep;", false);
        annotationVisitor0.visitEnd();
        // Intentionally introduce a duplication.
        annotationVisitor0 =
            methodVisitor.visitAnnotation("Lcom/android/tools/r8/d8/TestKeep;", false);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(31, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("TestA::foo");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(32, label1);
      methodVisitor.visitInsn(RETURN);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLocalVariable(
          "this", "Lcom/android/tools/r8/d8/TestA;", null, label0, label2, 0);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}

