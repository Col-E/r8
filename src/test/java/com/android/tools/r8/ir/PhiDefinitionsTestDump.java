// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class PhiDefinitionsTestDump implements Opcodes {

  private static final String SIMPLE_NAME = "PhiDefinitionsTest";
  static final String INTERNAL_NAME = "com/android/tools/r8/ir/" + SIMPLE_NAME;
  private static final String INNER_SIMPLE_NAME = "MethodWriter";
  static final String INNER_INTERNAL_NAME = INTERNAL_NAME + "$" + INNER_SIMPLE_NAME;

  // static String INTERNAL_NAME = "com/android/tools/r8/ir/PhiDefinitionsTest";
  // private static String INNER_SIMPLE_NAME = "MethodWriter";
  // static String INNER_INTERNAL_NAME = INTERNAL_NAME + '$' + INNER_SIMPLE_NAME;

  public static byte[] dump() throws Exception {

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    AnnotationVisitor av0;

    cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, INTERNAL_NAME, null, "java/lang/Object", null);

    cw.visitSource(SIMPLE_NAME + ".java", null);

    cw.visitInnerClass(INNER_INTERNAL_NAME, INTERNAL_NAME, INNER_SIMPLE_NAME, ACC_STATIC);

    method0(cw);
    method1(cw);
    method2(cw);
    nativeMethod(cw, "visitMethod", "()L" + INTERNAL_NAME + "$" + INNER_SIMPLE_NAME + ";");
    nativeMethod(cw, "cond", "()Z");
    nativeMethod(cw, "read", "(I)Ljava/lang/String;");
    nativeMethod(cw, "count", "(I)I");
    nativeMethod(cw, "count", "()I");
    cw.visitEnd();

    return cw.toByteArray();
  }

  private static void nativeMethod(ClassWriter cw, String name, String desc) {
    MethodVisitor mv;
    mv = cw.visitMethod(ACC_PRIVATE + ACC_NATIVE, name, desc, null, null);
    mv.visitEnd();
  }

  private static void method0(ClassWriter cw) {
    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    Label[] labels = new Label[2];
    for (int i = 0; i < labels.length; i++) {
      labels[i] = new Label();
    }
    mv.visitCode();
    mv.visitLabel(labels[0]);
    mv.visitLineNumber(6, labels[0]);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    mv.visitInsn(RETURN);
    mv.visitLabel(labels[1]);
    mv.visitLocalVariable("this", "L" + INTERNAL_NAME + ";", null, labels[0], labels[1], 0);
    mv.visitMaxs(1, 1);
    mv.visitEnd();
  }

  private static void method1(ClassWriter cw) {
    MethodVisitor mv =
        cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
    Label[] labels = new Label[4];
    for (int i = 0; i < labels.length; i++) {
      labels[i] = new Label();
    }
    mv.visitCode();
    mv.visitLabel(labels[0]);
    mv.visitLineNumber(18, labels[0]);
    mv.visitLdcInsn(new Integer(42));
    mv.visitVarInsn(ISTORE, 1);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitInsn(ARRAYLENGTH);
    mv.visitVarInsn(ILOAD, 1);
    mv.visitJumpInsn(IF_ICMPLT, labels[1]);
    mv.visitLabel(labels[2]);
    mv.visitLineNumber(19, labels[2]);
    mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    mv.visitVarInsn(ASTORE, 1);
    mv.visitTypeInsn(NEW, INTERNAL_NAME);
    mv.visitVarInsn(ASTORE, 2);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitMethodInsn(INVOKESPECIAL, INTERNAL_NAME, "<init>", "()V", false);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitInsn(ARRAYLENGTH);
    mv.visitVarInsn(ISTORE, 3);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitVarInsn(ILOAD, 3);
    mv.visitMethodInsn(INVOKESPECIAL, INTERNAL_NAME, "readMethod", "(I)I", false);
    mv.visitVarInsn(ISTORE, 2);
    mv.visitVarInsn(ALOAD, 1);
    mv.visitVarInsn(ILOAD, 2);
    mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
    mv.visitLabel(labels[1]);
    mv.visitLineNumber(21, labels[1]);
    mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
    mv.visitInsn(RETURN);
    mv.visitLabel(labels[3]);
    mv.visitLocalVariable("args", "[Ljava/lang/String;", null, labels[0], labels[3], 0);
    mv.visitMaxs(2, 4);
    mv.visitEnd();
  }

  private static void method2(ClassWriter cw) {
    MethodVisitor mv = cw.visitMethod(ACC_PRIVATE, "readMethod", "(I)I", null, null);
    Label[] labels = new Label[31];
    for (int i = 0; i < labels.length; i++) {
      labels[i] = new Label();
    }
    mv.visitCode();
    mv.visitLabel(labels[0]);
    mv.visitLineNumber(24, labels[0]);
    mv.visitLdcInsn(new Integer(6));
    mv.visitInsn(POP);
    mv.visitLdcInsn(new Integer(6));
    mv.visitVarInsn(ISTORE, 2);
    mv.visitVarInsn(ILOAD, 1);
    mv.visitVarInsn(ILOAD, 2);
    mv.visitInsn(IADD);
    mv.visitVarInsn(ISTORE, 1);
    mv.visitLabel(labels[1]);
    mv.visitLineNumber(25, labels[1]);
    mv.visitInsn(ICONST_0);
    mv.visitVarInsn(ISTORE, 3);
    mv.visitLabel(labels[2]);
    mv.visitLineNumber(26, labels[2]);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitVarInsn(ILOAD, 1);
    mv.visitMethodInsn(INVOKESPECIAL, INTERNAL_NAME, "count", "(I)I", false);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitVarInsn(ILOAD, 4);
    mv.visitVarInsn(ILOAD, 1);
    mv.visitVarInsn(ILOAD, 3);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitVarInsn(ISTORE, 3);
    mv.visitVarInsn(ISTORE, 1);
    mv.visitLabel(labels[3]);
    mv.visitFrame(
        Opcodes.F_APPEND,
        3,
        new Object[] {Opcodes.INTEGER, Opcodes.INTEGER, Opcodes.INTEGER},
        0,
        null);
    mv.visitVarInsn(ILOAD, 1);
    mv.visitVarInsn(ISTORE, 1);
    mv.visitVarInsn(ILOAD, 1);
    mv.visitJumpInsn(IFLE, labels[4]);
    mv.visitLabel(labels[5]);
    mv.visitLineNumber(27, labels[5]);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitVarInsn(ILOAD, 3);
    mv.visitMethodInsn(INVOKESPECIAL, INTERNAL_NAME, "count", "(I)I", false);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitLabel(labels[6]);
    mv.visitLineNumber(28, labels[6]);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKESPECIAL, INTERNAL_NAME, "count", "()I", false);
    mv.visitVarInsn(ISTORE, 5);
    mv.visitVarInsn(ILOAD, 5);
    mv.visitVarInsn(ILOAD, 4);
    mv.visitVarInsn(ISTORE, 5);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitLabel(labels[7]);
    mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
    mv.visitVarInsn(ILOAD, 4);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitVarInsn(ILOAD, 4);
    mv.visitJumpInsn(IFLE, labels[8]);
    mv.visitLabel(labels[9]);
    mv.visitLineNumber(29, labels[9]);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitVarInsn(ILOAD, 5);
    mv.visitMethodInsn(INVOKESPECIAL, INTERNAL_NAME, "read", "(I)Ljava/lang/String;", false);
    mv.visitInsn(POP);
    mv.visitLabel(labels[10]);
    mv.visitLineNumber(30, labels[10]);
    mv.visitInsn(ICONST_2);
    mv.visitInsn(POP);
    mv.visitInsn(ICONST_2);
    mv.visitVarInsn(ISTORE, 6);
    mv.visitVarInsn(ILOAD, 5);
    mv.visitVarInsn(ILOAD, 6);
    mv.visitInsn(IADD);
    mv.visitVarInsn(ISTORE, 5);
    mv.visitLabel(labels[11]);
    mv.visitLineNumber(28, labels[11]);
    mv.visitInsn(ICONST_M1);
    mv.visitInsn(POP);
    mv.visitInsn(ICONST_M1);
    mv.visitVarInsn(ISTORE, 6);
    mv.visitVarInsn(ILOAD, 4);
    mv.visitVarInsn(ILOAD, 6);
    mv.visitInsn(IADD);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitJumpInsn(GOTO, labels[7]);
    mv.visitLabel(labels[8]);
    mv.visitLineNumber(32, labels[8]);
    mv.visitFrame(
        Opcodes.F_FULL,
        6,
        new Object[] {
          INTERNAL_NAME,
          Opcodes.INTEGER,
          Opcodes.INTEGER,
          Opcodes.INTEGER,
          Opcodes.TOP,
          Opcodes.INTEGER
        },
        0,
        new Object[] {});
    mv.visitLdcInsn(new Integer(6));
    mv.visitInsn(POP);
    mv.visitInsn(ICONST_4);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitVarInsn(ILOAD, 3);
    mv.visitVarInsn(ILOAD, 4);
    mv.visitInsn(IADD);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitVarInsn(ILOAD, 4);
    mv.visitMethodInsn(INVOKESPECIAL, INTERNAL_NAME, "count", "(I)I", false);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitVarInsn(ILOAD, 2);
    mv.visitVarInsn(ILOAD, 4);
    mv.visitInsn(IADD);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitVarInsn(ILOAD, 3);
    mv.visitVarInsn(ILOAD, 4);
    mv.visitInsn(IADD);
    mv.visitVarInsn(ISTORE, 3);
    mv.visitLabel(labels[12]);
    mv.visitLineNumber(26, labels[12]);
    mv.visitInsn(ICONST_M1);
    mv.visitInsn(POP);
    mv.visitInsn(ICONST_M1);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitVarInsn(ILOAD, 1);
    mv.visitVarInsn(ILOAD, 4);
    mv.visitInsn(IADD);
    mv.visitVarInsn(ISTORE, 1);
    mv.visitVarInsn(ILOAD, 5);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitJumpInsn(GOTO, labels[3]);
    mv.visitLabel(labels[4]);
    mv.visitLineNumber(34, labels[4]);
    mv.visitFrame(
        Opcodes.F_FULL,
        5,
        new Object[] {INTERNAL_NAME, Opcodes.TOP, Opcodes.TOP, Opcodes.INTEGER, Opcodes.INTEGER},
        0,
        new Object[] {});
    mv.visitInsn(ICONST_2);
    mv.visitInsn(POP);
    mv.visitInsn(ICONST_2);
    mv.visitVarInsn(ISTORE, 1);
    mv.visitVarInsn(ILOAD, 3);
    mv.visitVarInsn(ILOAD, 1);
    mv.visitInsn(IADD);
    mv.visitVarInsn(ISTORE, 1);
    mv.visitLabel(labels[13]);
    mv.visitLineNumber(35, labels[13]);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(
        INVOKESPECIAL,
        INTERNAL_NAME,
        "visitMethod",
        "()L" + INTERNAL_NAME + "$" + INNER_SIMPLE_NAME + ";",
        false);
    mv.visitVarInsn(ASTORE, 2);
    mv.visitLabel(labels[14]);
    mv.visitLineNumber(36, labels[14]);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKESPECIAL, INTERNAL_NAME, "cond", "()Z", false);
    mv.visitJumpInsn(IFEQ, labels[15]);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKESPECIAL, INTERNAL_NAME, "cond", "()Z", false);
    mv.visitJumpInsn(IFEQ, labels[15]);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKESPECIAL, INTERNAL_NAME, "cond", "()Z", false);
    mv.visitJumpInsn(IFEQ, labels[15]);
    mv.visitLabel(labels[16]);
    mv.visitLineNumber(37, labels[16]);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitVarInsn(ASTORE, 3);
    mv.visitLabel(labels[17]);
    mv.visitLineNumber(38, labels[17]);
    mv.visitInsn(ICONST_0);
    mv.visitVarInsn(ISTORE, 5);
    mv.visitLabel(labels[18]);
    mv.visitLineNumber(39, labels[18]);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKESPECIAL, INTERNAL_NAME, "count", "()I", false);
    mv.visitVarInsn(ISTORE, 6);
    mv.visitVarInsn(ALOAD, 3);
    mv.visitFieldInsn(GETFIELD, INNER_INTERNAL_NAME, "exceptionCount", "I");
    mv.visitVarInsn(ISTORE, 7);
    mv.visitVarInsn(ILOAD, 6);
    mv.visitVarInsn(ILOAD, 7);
    mv.visitJumpInsn(IF_ICMPNE, labels[19]);
    mv.visitLabel(labels[20]);
    mv.visitLineNumber(40, labels[20]);
    mv.visitInsn(ICONST_1);
    mv.visitVarInsn(ISTORE, 5);
    mv.visitLabel(labels[21]);
    mv.visitLineNumber(41, labels[21]);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKESPECIAL, INTERNAL_NAME, "count", "()I", false);
    mv.visitVarInsn(ISTORE, 6);
    mv.visitVarInsn(ILOAD, 6);
    mv.visitVarInsn(ILOAD, 4);
    mv.visitVarInsn(ISTORE, 6);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitLabel(labels[22]);
    mv.visitFrame(
        Opcodes.F_FULL,
        7,
        new Object[] {
          INTERNAL_NAME,
          Opcodes.INTEGER,
          INNER_INTERNAL_NAME,
          INNER_INTERNAL_NAME,
          Opcodes.INTEGER,
          Opcodes.INTEGER,
          Opcodes.INTEGER
        },
        0,
        new Object[] {});
    mv.visitVarInsn(ILOAD, 4);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitVarInsn(ILOAD, 4);
    mv.visitJumpInsn(IFLT, labels[23]);
    mv.visitLabel(labels[24]);
    mv.visitLineNumber(42, labels[24]);
    mv.visitLdcInsn(new Integer(-2));
    mv.visitVarInsn(ISTORE, 7);
    mv.visitVarInsn(ILOAD, 6);
    mv.visitVarInsn(ILOAD, 7);
    mv.visitInsn(IADD);
    mv.visitVarInsn(ISTORE, 6);
    mv.visitLabel(labels[25]);
    mv.visitLineNumber(41, labels[25]);
    mv.visitInsn(ICONST_M1);
    mv.visitInsn(POP);
    mv.visitInsn(ICONST_M1);
    mv.visitVarInsn(ISTORE, 7);
    mv.visitVarInsn(ILOAD, 4);
    mv.visitVarInsn(ILOAD, 7);
    mv.visitInsn(IADD);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitJumpInsn(GOTO, labels[22]);
    mv.visitLabel(labels[23]);
    mv.visitFrame(
        Opcodes.F_FULL,
        7,
        new Object[] {
          INTERNAL_NAME,
          Opcodes.INTEGER,
          INNER_INTERNAL_NAME,
          INNER_INTERNAL_NAME,
          Opcodes.TOP,
          Opcodes.INTEGER,
          Opcodes.INTEGER
        },
        0,
        new Object[] {});
    mv.visitVarInsn(ILOAD, 5);
    mv.visitVarInsn(ILOAD, 6);
    mv.visitVarInsn(ISTORE, 5);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitJumpInsn(GOTO, labels[26]);
    mv.visitLabel(labels[19]);
    mv.visitFrame(
        Opcodes.F_FULL,
        6,
        new Object[] {
          INTERNAL_NAME,
          Opcodes.INTEGER,
          INNER_INTERNAL_NAME,
          INNER_INTERNAL_NAME,
          Opcodes.INTEGER,
          Opcodes.INTEGER
        },
        0,
        new Object[] {});
    mv.visitVarInsn(ILOAD, 5);
    mv.visitVarInsn(ILOAD, 4);
    mv.visitVarInsn(ISTORE, 5);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitLabel(labels[26]);
    mv.visitLineNumber(45, labels[26]);
    mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKESPECIAL, INTERNAL_NAME, "cond", "()Z", false);
    mv.visitJumpInsn(IFEQ, labels[27]);
    mv.visitLabel(labels[28]);
    mv.visitLineNumber(46, labels[28]);
    mv.visitVarInsn(ILOAD, 1);
    mv.visitInsn(IRETURN);
    mv.visitLabel(labels[27]);
    mv.visitFrame(
        Opcodes.F_FULL,
        6,
        new Object[] {
          INTERNAL_NAME,
          Opcodes.INTEGER,
          INNER_INTERNAL_NAME,
          Opcodes.TOP,
          Opcodes.TOP,
          Opcodes.INTEGER
        },
        0,
        new Object[] {});
    mv.visitVarInsn(ILOAD, 5);
    mv.visitVarInsn(ISTORE, 3);
    mv.visitJumpInsn(GOTO, labels[29]);
    mv.visitLabel(labels[15]);
    mv.visitFrame(
        Opcodes.F_FULL,
        5,
        new Object[] {
          INTERNAL_NAME, Opcodes.INTEGER, INNER_INTERNAL_NAME, Opcodes.TOP, Opcodes.INTEGER
        },
        0,
        new Object[] {});
    mv.visitVarInsn(ILOAD, 4);
    mv.visitVarInsn(ISTORE, 3);
    mv.visitLabel(labels[29]);
    mv.visitLineNumber(49, labels[29]);
    mv.visitFrame(
        Opcodes.F_FULL,
        4,
        new Object[] {INTERNAL_NAME, Opcodes.INTEGER, INNER_INTERNAL_NAME, Opcodes.INTEGER},
        0,
        new Object[] {});
    mv.visitVarInsn(ILOAD, 1);
    mv.visitInsn(IRETURN);
    mv.visitLabel(labels[30]);
    mv.visitLocalVariable("u", "I", null, labels[0], labels[3], 1);
    mv.visitLocalVariable("exception", "I", null, labels[2], labels[3], 3);
    mv.visitLocalVariable("exception", "I", null, labels[3], labels[7], 4);
    mv.visitLocalVariable("j", "I", null, labels[7], labels[8], 4);
    mv.visitLocalVariable("i", "I", null, labels[3], labels[4], 1);
    mv.visitLocalVariable("exception", "I", null, labels[7], labels[4], 5);
    mv.visitLocalVariable("u", "I", null, labels[3], labels[13], 3);
    mv.visitLocalVariable("exception", "I", null, labels[4], labels[22], 4);
    mv.visitLocalVariable("j", "I", null, labels[22], labels[23], 4);
    mv.visitLocalVariable("exception", "I", null, labels[22], labels[19], 6);
    mv.visitLocalVariable("exception", "I", null, labels[19], labels[26], 4);
    mv.visitLocalVariable("sameExceptions", "Z", null, labels[18], labels[26], 5);
    mv.visitLocalVariable(
        "mw", "L" + INTERNAL_NAME + "$" + INNER_SIMPLE_NAME + ";", null, labels[17], labels[27], 3);
    mv.visitLocalVariable("sameExceptions", "Z", null, labels[26], labels[27], 4);
    mv.visitLocalVariable("exception", "I", null, labels[26], labels[15], 5);
    mv.visitLocalVariable("exception", "I", null, labels[15], labels[29], 4);
    mv.visitLocalVariable("this", "L" + INTERNAL_NAME + ";", null, labels[0], labels[30], 0);
    mv.visitLocalVariable("u", "I", null, labels[13], labels[30], 1);
    mv.visitLocalVariable("exception", "I", null, labels[29], labels[30], 3);
    mv.visitLocalVariable(
        "mv", "L" + INTERNAL_NAME + "$" + INNER_SIMPLE_NAME + ";", null, labels[14], labels[30], 2);
    mv.visitMaxs(3, 8);
    mv.visitEnd();
  }

  public static byte[] dumpInner() throws Exception {

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;
    AnnotationVisitor av0;

    cw.visit(V1_8, ACC_SUPER, INNER_INTERNAL_NAME, null, "java/lang/Object", null);

    cw.visitSource(SIMPLE_NAME + ".java", null);

    cw.visitInnerClass(INNER_INTERNAL_NAME, INTERNAL_NAME, INNER_SIMPLE_NAME, ACC_STATIC);

    {
      fv = cw.visitField(ACC_PUBLIC, "exceptionCount", "I", null, null);
      fv.visitEnd();
    }
    {
      mv = cw.visitMethod(0, "<init>", "()V", null, null);
      mv.visitCode();
      Label l0 = new Label();
      mv.visitLabel(l0);
      mv.visitLineNumber(13, l0);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      Label l1 = new Label();
      mv.visitLabel(l1);
      mv.visitLocalVariable(
          "this", "L" + INTERNAL_NAME + "$" + INNER_SIMPLE_NAME + ";", null, l0, l1, 0);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }
}
