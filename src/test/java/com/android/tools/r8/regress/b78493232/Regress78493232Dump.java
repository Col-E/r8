// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b78493232;

import com.android.tools.r8.utils.DescriptorUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class Regress78493232Dump implements Opcodes {

  public static final String CLASS_NAME = "regress78493232.Test";
  public static final String CLASS_DESC = DescriptorUtils.javaTypeToDescriptor(CLASS_NAME);
  public static final String CLASS_INTERNAL = DescriptorUtils.descriptorToInternalName(CLASS_DESC);

  public static final String UTILS_CLASS_NAME = Regress78493232Utils.class.getCanonicalName();
  public static final String UTILS_CLASS_DESC =
      DescriptorUtils.javaTypeToDescriptor(UTILS_CLASS_NAME);
  public static final String UTILS_CLASS_INTERNAL =
      DescriptorUtils.descriptorToInternalName(UTILS_CLASS_DESC);

  static final int iterations = 1000;

  // Arguments that have been seen when issue occurred.
  static byte arg0 = 13;
  static short arg1 = 25;
  static int arg2 = 312;

  // Static state of the class seen when the issue occurred.
  static int staticIntA = 0;
  static int staticIntB = 29;
  static byte[] staticByteArray =
      new byte[] {
        63, 101, 52, -33, 9, -21, 21, 51, -59, -6, 65, -27, -37, -2, -5, 1, 33, -33, 2, 13, 4, -12,
        -53, 54, 2, -15, 46, 2, 13, 4, -3, 30, -47, 9, 0, -13, 3, -11, -10, 13, -2, 61, -69, -6, 6,
        -1, 15, -8, 63, -22, -33, -19, 50, -35, -3, 7, 8, 2, -7, 9, -21, 21, 51, -62, 11, -13, 7,
        57, -37, -38, 6, -1, 15, -8, -53, 3, -19, 19, 50, -53, 3, -19, 19, 50, 9, -21, 21, 51, -59,
        -6, 65, -27, -6, 10, -51, 21, -2, -11, -4, 11, -6, 1, 1, 11, -3, 61, -50, 50, -75, 75, -52,
        0, 52, -53, -9, -3, -4, 14, 2, -15, 49, -41, 11, -18, 0, 39, -35, 14, -3, -1, -13, 9, -21,
        21, 51, -59, -6, 65, -70, 7, -3, 12, -5, -9, 2, -13, 23, -27, 9, -11, 15, -6, 11, 11, 11, 7,
        16, -31, -2, 4, 7, 9, -21, 21, 51, -69, 14, 2, -18, 3, 9, -11, -5, 75, -37, -18, 2, -18, 3,
        13, 19, -15, -13, 10, -11, 2, -1, -7, 7, -15, 15, 5, 9, -11, 15, 13, 4, -3, -18, 3, 0, 13,
        -9, -6, 32, -21, -4, 8, 24, -28, -3, 0, 3, -10, 9, -21, 21, 51, -59, -6, 65, -20, -55, 5,
        15, 36, -49, 0, 17, -24, 48, -37, -2, -5, 1, 33, -33, 2, 13, 4, -12, 9, -21, 21, 51, -59,
        -6, 65, -24, -35, -3, 7, 22, -38, 1, 4, -5, 1, 33, -33, 2, 13, 4, -12, 1, 11, -3, 61, -50,
        50, -75, 75, -52, 0, 52, -52, 63, -77, 2, -15, 47, -51, 4, 15, -13, 4, 13, -11, 25, -33, 5,
        -3, 17, -6, 2, 33, -37, -9, 13, 2, -17, 5, -3, -7, -3, 14, -3, 33, -41, 11, -18, 0, 2, -15,
        43, -37, -5, -1, 19, -13, 11, -2, -13, 10, -14, 3, 6, 5, 54, -65, -4, 69, -23, -41, -8, 13,
        -9, 3, 1, 1, 8, -9, -6, 21, -4, 20, -8, 9, -21, 21, 51, -62, 11, -13, 7, 57, -21, -41, 11,
        -18, 0, 39, -35, 14, -3, -1, -13, -3, 14, -3, 32, -33, -19, 1, 11, -3, 62, -51, 51, -76, 76,
        -53, 0, 53, -54, 14, -15, 33, -18, 0, 1, 9, -21, 21, 51, -59, -6, 65, -22, -29, -19, 19, 24,
        -37, -2, -5, 1, 33, -33, 2, 13, 4, -12, 2, -15, 36, -34, 3, -1, 11, -13, -2, -5, 2, -15, 51,
        -33, -17, 4, 3, -9, 1, 15, 21, -17, -19, 12, 9, -21, 21, 51, -59, -6, 65, -24, -35, -3, 7,
        9, -21, 21, 51, -59, -6, 65, -24, -35, -3, 7, 33, -33, -14, 16, -15, 9, -7, -4, 5, -3, 21,
        -3, 19, -8, 9, -19, 4, 43, -37, -6
      };

  public static byte[] dump() {
    return dump(arg0, arg1, arg2, staticIntA, staticIntB, staticByteArray);
  }

  public static byte[] dump(
      byte arg0, short arg1, int arg2, int staticIntA, int staticIntB, byte[] staticByteArray) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    FieldVisitor fv;
    MethodVisitor mv;

    cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, CLASS_INTERNAL, null, "java/lang/Object", null);

    {
      fv = cw.visitField(ACC_PRIVATE + ACC_FINAL + ACC_STATIC, "staticByteArray", "[B", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_PRIVATE + ACC_STATIC, "staticIntA", "I", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_PRIVATE + ACC_STATIC, "staticIntB", "I", null, null);
      fv.visitEnd();
    }

    {
      mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "run", "()Ljava/lang/String;", null, null);
      mv.visitCode();
      mv.visitIntInsn(BIPUSH, arg0);
      mv.visitIntInsn(SIPUSH, arg1);
      mv.visitIntInsn(SIPUSH, arg2);
      mv.visitMethodInsn(
          INVOKESTATIC, CLASS_INTERNAL, "methodCausingIssue", "(BSI)Ljava/lang/String;", false);
      mv.visitInsn(ARETURN);
      mv.visitMaxs(-1, -1);
      mv.visitEnd();
    }

    {
      mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "getHash", "()I", null, null);
      mv.visitCode();
      getHash(mv);
      mv.visitInsn(IRETURN);
      mv.visitMaxs(-1, -1);
      mv.visitEnd();
    }

    {
      mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      mv.visitCode();

      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ISTORE, 1);
      Label l0 = new Label();
      mv.visitLabel(l0);
      mv.visitVarInsn(ILOAD, 1);
      mv.visitLdcInsn(new Integer(iterations));
      Label l1 = new Label();
      mv.visitJumpInsn(IF_ICMPGE, l1);
      mv.visitMethodInsn(INVOKESTATIC, CLASS_INTERNAL, "run", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(
          INVOKESTATIC, UTILS_CLASS_INTERNAL, "compare", "(Ljava/lang/String;I)V", false);
      mv.visitFieldInsn(GETSTATIC, CLASS_INTERNAL, "staticIntA", "I");
      mv.visitFieldInsn(GETSTATIC, CLASS_INTERNAL, "staticIntB", "I");
      mv.visitFieldInsn(GETSTATIC, CLASS_INTERNAL, "staticByteArray", "[B");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKESTATIC, UTILS_CLASS_INTERNAL, "compareHash", "(II[BI)V", false);
      mv.visitIincInsn(1, 1);
      mv.visitJumpInsn(GOTO, l0);
      mv.visitLabel(l1);
      mv.visitLdcInsn("Completed successfully after " + iterations + " iterations");
      mv.visitMethodInsn(
          INVOKESTATIC, UTILS_CLASS_INTERNAL, "println", "(Ljava/lang/String;)V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(-1, -1);
      mv.visitEnd();
    }

    {
      mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      mv.visitCode();
      mv.visitIntInsn(BIPUSH, staticIntA);
      mv.visitFieldInsn(PUTSTATIC, CLASS_INTERNAL, "staticIntA", "I");
      mv.visitIntInsn(BIPUSH, staticIntB);
      mv.visitFieldInsn(PUTSTATIC, CLASS_INTERNAL, "staticIntB", "I");
      mv.visitLdcInsn(staticByteArray.length);
      mv.visitIntInsn(NEWARRAY, T_BYTE);
      for (int i = 0; i < staticByteArray.length; i++) {
        mv.visitInsn(DUP);
        mv.visitIntInsn(SIPUSH, i);
        mv.visitIntInsn(BIPUSH, staticByteArray[i]);
        mv.visitInsn(BASTORE);
      }
      mv.visitFieldInsn(PUTSTATIC, CLASS_INTERNAL, "staticByteArray", "[B");
      mv.visitInsn(RETURN);
      mv.visitMaxs(-1, -1);
      mv.visitEnd();
    }

    {
      mv =
          cw.visitMethod(
              ACC_PRIVATE + ACC_STATIC,
              "methodCausingIssue",
              "(BSI)Ljava/lang/String;",
              null,
              null);
      mv.visitCode();
      Label l0 = new Label();
      mv.visitJumpInsn(GOTO, l0);
      mv.visitLabel(l0);
      mv.visitIntInsn(SIPUSH, 472);
      mv.visitVarInsn(ILOAD, 2);
      mv.visitInsn(ISUB);
      mv.visitVarInsn(ISTORE, 2);
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ISTORE, 3);
      mv.visitTypeInsn(NEW, "java/lang/String");
      mv.visitInsn(DUP);
      mv.visitIntInsn(BIPUSH, 119);
      mv.visitVarInsn(ILOAD, 0);
      mv.visitInsn(ISUB);
      mv.visitVarInsn(ISTORE, 0);
      mv.visitIincInsn(1, 1);
      mv.visitFieldInsn(GETSTATIC, CLASS_INTERNAL, "staticByteArray", "[B");
      mv.visitVarInsn(ASTORE, 4);
      mv.visitVarInsn(ILOAD, 1);
      mv.visitIntInsn(NEWARRAY, T_BYTE);
      mv.visitVarInsn(ALOAD, 4);
      Label l1 = new Label();
      mv.visitJumpInsn(IFNONNULL, l1);
      Label l2 = new Label();
      mv.visitJumpInsn(GOTO, l2);
      mv.visitLabel(l1);
      Label l3 = new Label();
      mv.visitJumpInsn(GOTO, l3);
      mv.visitLabel(l2);
      mv.visitVarInsn(ILOAD, 1);
      mv.visitVarInsn(ILOAD, 2);
      Label l4 = new Label();
      mv.visitJumpInsn(GOTO, l4);
      Label l5 = new Label();
      mv.visitLabel(l5);
      mv.visitInsn(INEG);
      mv.visitInsn(IADD);
      mv.visitVarInsn(ISTORE, 0);
      mv.visitJumpInsn(GOTO, l3);
      mv.visitLabel(l3);
      mv.visitInsn(DUP);
      mv.visitVarInsn(ILOAD, 3);
      mv.visitIincInsn(3, 1);
      mv.visitVarInsn(ILOAD, 0);
      mv.visitInsn(I2B);
      mv.visitInsn(BASTORE);
      mv.visitVarInsn(ILOAD, 3);
      mv.visitVarInsn(ILOAD, 1);
      Label l6 = new Label();
      mv.visitJumpInsn(IF_ICMPNE, l6);
      Label l7 = new Label();
      mv.visitJumpInsn(GOTO, l7);
      mv.visitLabel(l6);
      Label l8 = new Label();
      mv.visitJumpInsn(GOTO, l8);
      mv.visitLabel(l7);
      mv.visitInsn(ICONST_0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/String", "<init>", "([BI)V", false);
      mv.visitInsn(ARETURN);
      mv.visitLabel(l8);
      mv.visitVarInsn(ILOAD, 0);
      mv.visitVarInsn(ALOAD, 4);
      mv.visitIincInsn(2, 1);
      mv.visitVarInsn(ILOAD, 2);
      mv.visitInsn(BALOAD);
      Label l9 = new Label();
      mv.visitJumpInsn(GOTO, l9);
      mv.visitLabel(l4);
      mv.visitFieldInsn(GETSTATIC, CLASS_INTERNAL, "staticIntB", "I");
      mv.visitIntInsn(BIPUSH, 125);
      mv.visitInsn(IADD);
      mv.visitInsn(DUP);
      mv.visitIntInsn(SIPUSH, 128);
      mv.visitInsn(IREM);
      mv.visitFieldInsn(PUTSTATIC, CLASS_INTERNAL, "staticIntA", "I");
      mv.visitInsn(ICONST_2);
      mv.visitInsn(IREM);
      Label l10 = new Label();
      mv.visitJumpInsn(IFEQ, l10);
      Label l11 = new Label();
      mv.visitJumpInsn(GOTO, l11);
      mv.visitLabel(l10);
      Label l12 = new Label();
      mv.visitJumpInsn(GOTO, l12);
      Label l13 = new Label();
      mv.visitLabel(l13);
      mv.visitInsn(INEG);
      mv.visitInsn(IADD);
      mv.visitVarInsn(ISTORE, 0);
      mv.visitJumpInsn(GOTO, l3);
      mv.visitLabel(l9);
      mv.visitFieldInsn(GETSTATIC, CLASS_INTERNAL, "staticIntA", "I");
      mv.visitIntInsn(BIPUSH, 29);
      mv.visitInsn(IADD);
      mv.visitInsn(DUP);
      mv.visitIntInsn(SIPUSH, 128);
      mv.visitInsn(IREM);
      mv.visitFieldInsn(PUTSTATIC, CLASS_INTERNAL, "staticIntB", "I");
      mv.visitInsn(ICONST_2);
      mv.visitInsn(IREM);
      Label l14 = new Label();
      mv.visitJumpInsn(IFNE, l14);
      Label l15 = new Label();
      mv.visitJumpInsn(GOTO, l15);
      mv.visitLabel(l14);
      Label l16 = new Label();
      mv.visitJumpInsn(GOTO, l16);
      Label l17 = new Label();
      mv.visitLabel(l17);
      mv.visitInsn(INEG);
      mv.visitInsn(IADD);
      mv.visitVarInsn(ISTORE, 0);
      mv.visitJumpInsn(GOTO, l3);
      Label l18 = new Label();
      mv.visitLabel(l18);
      mv.visitTableSwitchInsn(0, 1, l11, new Label[] {l13, l5});
      mv.visitLabel(l12);
      mv.visitInsn(ICONST_1);
      mv.visitJumpInsn(GOTO, l18);
      mv.visitLabel(l11);
      mv.visitInsn(ICONST_0);
      mv.visitJumpInsn(GOTO, l18);
      Label l19 = new Label();
      mv.visitLabel(l19);
      mv.visitTableSwitchInsn(0, 1, l15, new Label[] {l5, l17});
      mv.visitLabel(l16);
      mv.visitInsn(ICONST_0);
      mv.visitJumpInsn(GOTO, l19);
      mv.visitLabel(l15);
      mv.visitInsn(ICONST_1);
      mv.visitJumpInsn(GOTO, l19);
      mv.visitMaxs(8, 5);
      mv.visitEnd();
    }

    cw.visitEnd();

    return cw.toByteArray();
  }

  private static void getHash(MethodVisitor mv) {
    mv.visitFieldInsn(GETSTATIC, CLASS_INTERNAL, "staticIntA", "I");
    mv.visitFieldInsn(GETSTATIC, CLASS_INTERNAL, "staticIntB", "I");
    mv.visitFieldInsn(GETSTATIC, CLASS_INTERNAL, "staticByteArray", "[B");
    mv.visitMethodInsn(INVOKESTATIC, UTILS_CLASS_INTERNAL, "getHash", "(II[B)I", false);
  }

}
