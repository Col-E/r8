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

public class KotlinReflectionDump implements Opcodes {

  private static final String reflectionFactory =
      "java/lang/Object"; // "kotlin/jvm/internal/ReflectionFactory";
  private static final String kClass = "java/lang/Object"; // "kotlin/reflect/KClass";
  private static final String INTERNAL_NAME = "kotlin/jvm/internal/Reflection";
  public static final String CLASS_NAME = INTERNAL_NAME.replace('/', '.');

  public static byte[] dump() throws Exception {

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;
    AnnotationVisitor av0;

    cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, INTERNAL_NAME, null, "java/lang/Object", null);

    cw.visitSource("Reflection.java", null);

    {
      fv =
          cw.visitField(
              ACC_PRIVATE + ACC_FINAL + ACC_STATIC,
              "factory",
              "L" + reflectionFactory + ";",
              null,
              null);
      fv.visitEnd();
    }
    {
      fv =
          cw.visitField(
              ACC_FINAL + ACC_STATIC,
              "REFLECTION_NOT_AVAILABLE",
              "Ljava/lang/String;",
              null,
              " (Kotlin reflection is not available)");
      fv.visitEnd();
    }
    {
      fv =
          cw.visitField(
              ACC_PRIVATE + ACC_FINAL + ACC_STATIC,
              "EMPTY_K_CLASS_ARRAY",
              "[L" + kClass + ";",
              null,
              null);
      fv.visitEnd();
    }
    method0(cw);
    mainMethod(cw);
    cw.visitEnd();

    return cw.toByteArray();
  }

  private static void mainMethod(ClassWriter cw) {
    MethodVisitor mv =
        cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
    mv.visitCode();
    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 1);
    mv.visitEnd();
  }

  private static void method0(ClassWriter cw) {
    MethodVisitor mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
    Label[] labels = new Label[18];
    for (int i = 0; i < labels.length; i++) {
      labels[i] = new Label();
    }
    mv.visitCode();
    mv.visitTryCatchBlock(labels[0], labels[1], labels[2], "java/lang/ClassCastException");
    mv.visitTryCatchBlock(labels[0], labels[1], labels[3], "java/lang/ClassNotFoundException");
    mv.visitTryCatchBlock(labels[0], labels[1], labels[4], "java/lang/InstantiationException");
    mv.visitTryCatchBlock(labels[0], labels[1], labels[5], "java/lang/IllegalAccessException");
    mv.visitLabel(labels[0]);
    mv.visitLineNumber(33, labels[0]);
    mv.visitLdcInsn("kotlin.reflect.jvm.internal.ReflectionFactoryImpl");
    mv.visitMethodInsn(
        INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
    mv.visitVarInsn(ASTORE, 1);
    mv.visitLabel(labels[6]);
    mv.visitLineNumber(34, labels[6]);
    mv.visitVarInsn(ALOAD, 1);
    mv.visitMethodInsn(
        INVOKEVIRTUAL, "java/lang/Class", "newInstance", "()Ljava/lang/Object;", false);
    mv.visitTypeInsn(CHECKCAST, reflectionFactory);
    mv.visitVarInsn(ASTORE, 0);
    mv.visitLabel(labels[1]);
    mv.visitLineNumber(39, labels[1]);
    mv.visitJumpInsn(GOTO, labels[7]);
    mv.visitLabel(labels[2]);
    mv.visitLineNumber(36, labels[2]);
    mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/ClassCastException"});
    mv.visitVarInsn(ASTORE, 1);
    mv.visitLabel(labels[8]);
    mv.visitInsn(ACONST_NULL);
    mv.visitVarInsn(ASTORE, 0);
    mv.visitLabel(labels[9]);
    mv.visitLineNumber(39, labels[9]);
    mv.visitJumpInsn(GOTO, labels[7]);
    mv.visitLabel(labels[3]);
    mv.visitLineNumber(37, labels[3]);
    mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/ClassNotFoundException"});
    mv.visitVarInsn(ASTORE, 1);
    mv.visitLabel(labels[10]);
    mv.visitInsn(ACONST_NULL);
    mv.visitVarInsn(ASTORE, 0);
    mv.visitLabel(labels[11]);
    mv.visitLineNumber(39, labels[11]);
    mv.visitJumpInsn(GOTO, labels[7]);
    mv.visitLabel(labels[4]);
    mv.visitLineNumber(38, labels[4]);
    mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/InstantiationException"});
    mv.visitVarInsn(ASTORE, 1);
    mv.visitLabel(labels[12]);
    mv.visitInsn(ACONST_NULL);
    mv.visitVarInsn(ASTORE, 0);
    mv.visitLabel(labels[13]);
    mv.visitLineNumber(39, labels[13]);
    mv.visitJumpInsn(GOTO, labels[7]);
    mv.visitLabel(labels[5]);
    mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/IllegalAccessException"});
    mv.visitVarInsn(ASTORE, 1);
    mv.visitLabel(labels[14]);
    mv.visitInsn(ACONST_NULL);
    mv.visitVarInsn(ASTORE, 0);
    mv.visitLabel(labels[7]);
    mv.visitLineNumber(41, labels[7]);
    mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {reflectionFactory}, 0, null);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitJumpInsn(IFNULL, labels[15]);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitJumpInsn(GOTO, labels[16]);
    mv.visitLabel(labels[15]);
    mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
    mv.visitTypeInsn(NEW, reflectionFactory);
    mv.visitInsn(DUP);
    mv.visitMethodInsn(INVOKESPECIAL, reflectionFactory, "<init>", "()V", false);
    mv.visitLabel(labels[16]);
    mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {reflectionFactory});
    mv.visitFieldInsn(PUTSTATIC, INTERNAL_NAME, "factory", "L" + reflectionFactory + ";");
    mv.visitLabel(labels[17]);
    mv.visitLineNumber(46, labels[17]);
    mv.visitInsn(ICONST_0);
    mv.visitTypeInsn(ANEWARRAY, kClass);
    mv.visitFieldInsn(PUTSTATIC, INTERNAL_NAME, "EMPTY_K_CLASS_ARRAY", "[L" + kClass + ";");
    mv.visitInsn(RETURN);
    mv.visitLocalVariable(
        "implClass", "Ljava/lang/Class;", "Ljava/lang/Class<*>;", labels[6], labels[1], 1);
    mv.visitLocalVariable("e", "Ljava/lang/ClassCastException;", null, labels[8], labels[9], 1);
    mv.visitLocalVariable(
        "e", "Ljava/lang/ClassNotFoundException;", null, labels[10], labels[11], 1);
    mv.visitLocalVariable(
        "e", "Ljava/lang/InstantiationException;", null, labels[12], labels[13], 1);
    mv.visitLocalVariable(
        "e", "Ljava/lang/IllegalAccessException;", null, labels[14], labels[7], 1);
    mv.visitLocalVariable("impl", "L" + reflectionFactory + ";", null, labels[1], labels[17], 0);
    mv.visitMaxs(2, 2);
    mv.visitEnd();
  }
}
