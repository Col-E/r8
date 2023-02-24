// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner.Regress134304597;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class TestDump implements Opcodes {

  // Generated from Test.java with tools/asmifier.py. Change commented out below
  public static byte[] dump() {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter
        .visit(V1_8, ACC_SUPER, "com/android/tools/r8/ir/optimize/inliner/Regress134304597/Test",
            null, "java/lang/Object", null);

    classWriter.visitSource("Test.java", null);

    {
      fieldVisitor = classWriter.visitField(ACC_PUBLIC, "i", "I", null, null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor = classWriter.visitField(ACC_PRIVATE, "j", "I", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(7, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(4, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitIntInsn(BIPUSH, 33);
      methodVisitor.visitFieldInsn(PUTFIELD,
          "com/android/tools/r8/ir/optimize/inliner/Regress134304597/Test", "i", "I");
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(5, label2);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitIntInsn(BIPUSH, 42);
      methodVisitor.visitFieldInsn(PUTFIELD,
          "com/android/tools/r8/ir/optimize/inliner/Regress134304597/Test", "j", "I");
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(8, label3);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor
          .visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
      methodVisitor.visitLdcInsn(new Long(42L));
      methodVisitor.visitInsn(LCMP);
      Label label4 = new Label();
      methodVisitor.visitJumpInsn(IFNE, label4);
      methodVisitor.visitInsn(ICONST_0);
      Label label5 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label5);
      methodVisitor.visitLabel(label4);
      methodVisitor.visitFrame(Opcodes.F_FULL, 1,
          new Object[]{"com/android/tools/r8/ir/optimize/inliner/Regress134304597/Test"}, 1,
          new Object[]{"com/android/tools/r8/ir/optimize/inliner/Regress134304597/Test"});
      methodVisitor.visitIntInsn(BIPUSH, 1);
      methodVisitor.visitLabel(label5);
      methodVisitor.visitFrame(Opcodes.F_FULL, 1,
          new Object[]{"com/android/tools/r8/ir/optimize/inliner/Regress134304597/Test"}, 2,
          new Object[]{"com/android/tools/r8/ir/optimize/inliner/Regress134304597/Test",
              Opcodes.INTEGER});
      methodVisitor.visitFieldInsn(PUTFIELD,
          "com/android/tools/r8/ir/optimize/inliner/Regress134304597/Test", "i", "I");
      Label label6 = new Label();
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(9, label6);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(GETFIELD,
          "com/android/tools/r8/ir/optimize/inliner/Regress134304597/Test", "i", "I");
      methodVisitor.visitInsn(ICONST_3);
      methodVisitor.visitInsn(IADD);
      methodVisitor.visitFieldInsn(PUTFIELD,
          "com/android/tools/r8/ir/optimize/inliner/Regress134304597/Test", "j", "I");
      Label label7 = new Label();
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(10, label7);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(5, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "getValue", "()Z", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(13, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(GETFIELD,
          "com/android/tools/r8/ir/optimize/inliner/Regress134304597/Test", "i", "I");
      // Removed code to change:
      // if (i > 0) return true;
      // into
      // return i;
      /*  Label label1 = new Label();
      methodVisitor.visitJumpInsn(IFLE, label1);
      methodVisitor.visitInsn(ICONST_1);
      Label label2 = new Label();
      methodVisitor.visitJumpInsn(GOTO, label2);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{Opcodes.INTEGER});*/
      methodVisitor.visitInsn(IRETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "printValue", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(17, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(GETFIELD,
          "com/android/tools/r8/ir/optimize/inliner/Regress134304597/Test", "j", "I");
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(18, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}