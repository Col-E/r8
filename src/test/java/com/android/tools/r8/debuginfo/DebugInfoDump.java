// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class DebugInfoDump implements Opcodes {

  private static final String INTERNAL_NAME = "Foo";
  public static final String CLASS_NAME = INTERNAL_NAME.replace('/', '.');

  public static byte[] dump() throws Exception {

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    AnnotationVisitor av0;

    cw.visit(V1_7, ACC_PUBLIC + ACC_SUPER, INTERNAL_NAME, null, "java/lang/Object", null);

    {
      fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, "f1", "L" + INTERNAL_NAME + ";", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, "f2", "Ljava/util/List;", null, null);
      fv.visitEnd();
    }
    foo(cw);
    main(cw);
    bar(cw);
    cw.visitEnd();

    return cw.toByteArray();
  }

  private static void bar(ClassWriter cw) {
    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "bar", "()I", null, null);
    mv.visitCode();
    mv.visitLdcInsn(42);
    mv.visitInsn(IRETURN);
    mv.visitMaxs(1, 1);
    mv.visitEnd();
  }

  private static void main(ClassWriter cw) {
    MethodVisitor mv =
        cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
    mv.visitCode();
    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 1);
    mv.visitEnd();
  }

  private static void foo(ClassWriter cw) {
    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "foo", "()I", null, null);
    Label[] labels = new Label[11];
    for (int i = 0; i < labels.length; i++) {
      labels[i] = new Label();
    }
    mv.visitCode();
    mv.visitLabel(labels[0]);
    mv.visitLineNumber(10, labels[0]);
    mv.visitIntInsn(BIPUSH, 12);
    mv.visitVarInsn(ISTORE, 1);
    mv.visitLabel(labels[1]);
    mv.visitLineNumber(11, labels[1]);
    mv.visitIincInsn(1, 28);
    mv.visitLabel(labels[2]);
    mv.visitLineNumber(13, labels[2]);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitFieldInsn(GETFIELD, INTERNAL_NAME, "f1", "L" + INTERNAL_NAME + ";");
    mv.visitJumpInsn(IFNULL, labels[3]);
    mv.visitLabel(labels[4]);
    mv.visitLineNumber(14, labels[4]);
    mv.visitVarInsn(ILOAD, 1);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitFieldInsn(GETFIELD, INTERNAL_NAME, "f1", "L" + INTERNAL_NAME + ";");
    mv.visitMethodInsn(INVOKEVIRTUAL, INTERNAL_NAME, "bar", "()I", false);
    mv.visitInsn(IADD);
    mv.visitVarInsn(ISTORE, 1);
    mv.visitLabel(labels[3]);
    mv.visitLineNumber(17, labels[3]);
    mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitFieldInsn(GETFIELD, INTERNAL_NAME, "f2", "Ljava/util/List;");
    mv.visitJumpInsn(IFNULL, labels[5]);
    mv.visitLabel(labels[6]);
    mv.visitLineNumber(18, labels[6]);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitFieldInsn(GETFIELD, INTERNAL_NAME, "f2", "Ljava/util/List;");
    mv.visitMethodInsn(
        INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
    mv.visitVarInsn(ASTORE, 2);
    mv.visitLabel(labels[7]);
    mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {"java/util/Iterator"}, 0, null);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
    mv.visitJumpInsn(IFEQ, labels[5]);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
    mv.visitTypeInsn(CHECKCAST, INTERNAL_NAME);
    mv.visitVarInsn(ASTORE, 3);
    mv.visitLabel(labels[8]);
    mv.visitLineNumber(19, labels[8]);
    mv.visitVarInsn(ILOAD, 1);
    mv.visitVarInsn(ALOAD, 3);
    mv.visitInsn(POP);
    mv.visitMethodInsn(INVOKESTATIC, INTERNAL_NAME, "foo", "()I", false);
    mv.visitInsn(IADD);
    mv.visitVarInsn(ISTORE, 1);
    mv.visitLabel(labels[9]);
    mv.visitLineNumber(20, labels[9]);
    mv.visitJumpInsn(GOTO, labels[7]);
    mv.visitLabel(labels[5]);
    mv.visitLineNumber(23, labels[5]);
    mv.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
    mv.visitVarInsn(ILOAD, 1);
    mv.visitInsn(IRETURN);
    mv.visitLabel(labels[10]);
    mv.visitLocalVariable("b", "L" + INTERNAL_NAME + ";", null, labels[8], labels[9], 3);
    mv.visitLocalVariable("this", "L" + INTERNAL_NAME + ";", null, labels[0], labels[10], 0);
    mv.visitLocalVariable("a", "I", null, labels[1], labels[10], 1);
    mv.visitMaxs(2, 4);
    mv.visitEnd();
  }
}
