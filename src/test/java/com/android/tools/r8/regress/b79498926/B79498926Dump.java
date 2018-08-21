// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b79498926;

import com.android.tools.r8.utils.DescriptorUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class B79498926Dump implements Opcodes {

  public static final String CLASS_NAME = "Test";
  public static final String CLASS_DESC = DescriptorUtils.javaTypeToDescriptor(CLASS_NAME);
  public static final String CLASS_INTERNAL = DescriptorUtils.descriptorToInternalName(CLASS_DESC);

  public static byte[] dump() {

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;

    cw.visit(V1_7, ACC_PUBLIC + ACC_SUPER, "Test", null, "java/lang/Object", null);

    {
      fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "a", "Lident102;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "b", "Lident103;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_STATIC, "c", "I", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_STATIC, "d", "Lident104;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_STATIC, "e", "Lident106;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_STATIC, "f", "Lident107;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_STATIC, "g", "Lident109;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_STATIC, "h", "Ljava/util/List;", null, null);
      fv.visitEnd();
    }
    {
      fv =
          cw.visitField(
              ACC_STATIC, "i", "Landroid/view/inputmethod/InputMethodManager;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_STATIC, "j", "Ljava/text/DecimalFormat;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_PRIVATE + ACC_STATIC, "k", "Ljava/lang/String;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_PRIVATE + ACC_STATIC, "l", "Ljava/lang/String;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_PRIVATE + ACC_STATIC, "m", "Ljava/lang/String;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_PRIVATE + ACC_STATIC, "n", "Ljava/lang/String;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_PRIVATE + ACC_STATIC, "o", "Ljava/lang/String;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_STATIC, "p", "Z", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_STATIC, "q", "Ljava/lang/String;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_STATIC, "r", "Ljava/lang/String;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_STATIC, "s", "Ljava/lang/String;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_STATIC, "t", "Ljava/lang/String;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_STATIC, "u", "Landroid/widget/ArrayAdapter;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "v", "Ljava/lang/String;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "w", "Landroid/widget/EditText;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "x", "Landroid/widget/EditText;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "y", "Landroid/widget/EditText;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "z", "Landroid/widget/EditText;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "A", "Landroid/widget/EditText;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "B", "Landroid/widget/EditText;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "C", "Landroid/widget/EditText;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "D", "Landroid/widget/EditText;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "E", "Landroid/widget/EditText;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "F", "Z", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "G", "Landroid/widget/EditText;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "H", "Landroid/widget/EditText;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "I", "Landroid/widget/EditText;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "J", "Landroid/widget/EditText;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "K", "Landroid/widget/EditText;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "L", "Landroid/widget/EditText;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "M", "Z", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "N", "Z", null, null);
      fv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "method1", "(I)V", null, null);
      mv.visitCode();
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 2);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 3);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 4);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 5);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 6);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 7);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 8);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 10);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 11);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 13);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 15);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 17);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 19);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 21);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 23);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 25);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 27);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 29);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 31);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 32);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 34);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 36);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 38);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 40);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 42);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 44);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 46);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 48);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 50);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 52);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 53);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 54);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 56);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 57);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 58);
      mv.visitInsn(LCONST_0);
      mv.visitVarInsn(LSTORE, 60);
      mv.visitInsn(LCONST_0);
      mv.visitVarInsn(LSTORE, 62);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 64);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 66);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 68);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 69);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 70);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 71);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 72);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 73);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 75);
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ISTORE, 77);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 78);
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ISTORE, 79);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 80);
      mv.visitInsn(ACONST_NULL);
      mv.visitVarInsn(ASTORE, 81);
      mv.visitInsn(ACONST_NULL);
      mv.visitVarInsn(ASTORE, 82);
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ISTORE, 83);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 84);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "Test", "method131", "()Landroid/support/v4/app/FragmentManager;", false);
      mv.visitLdcInsn(new Integer(2131231617));
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/support/v4/app/FragmentManager",
          "findFragmentById",
          "(I)Landroid/support/v4/app/Fragment;",
          false);
      mv.visitTypeInsn(CHECKCAST, "ident132");
      mv.visitVarInsn(ASTORE, 85);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitInsn(ICONST_0);
      mv.visitFieldInsn(PUTFIELD, "Test", "F", "Z");
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ISTORE, 83);
      mv.visitVarInsn(ILOAD, 1);
      mv.visitFieldInsn(PUTSTATIC, "Test", "c", "I");
      mv.visitVarInsn(ALOAD, 0);
      mv.visitInsn(ICONST_0);
      mv.visitFieldInsn(PUTFIELD, "Test", "M", "Z");
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method146", "()I", false);
      Label l0 = new Label();
      mv.visitJumpInsn(IFLE, l0);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method149", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 2);
      mv.visitVarInsn(ALOAD, 2);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitLdcInsn("");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l1 = new Label();
      mv.visitJumpInsn(IFNE, l1);
      mv.visitTypeInsn(NEW, "ident104");
      mv.visitInsn(DUP);
      mv.visitFieldInsn(GETSTATIC, "Test", "a", "Lident102;");
      mv.visitVarInsn(ALOAD, 2);
      mv.visitMethodInsn(
          INVOKESPECIAL, "ident104", "<init>", "(Lident102;Ljava/lang/String;)V", false);
      mv.visitFieldInsn(PUTSTATIC, "Test", "d", "Lident104;");
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method150", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 57);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method151", "()D", false);
      mv.visitVarInsn(DSTORE, 58);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method152", "()J", false);
      mv.visitVarInsn(LSTORE, 60);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method153", "()J", false);
      mv.visitVarInsn(LSTORE, 62);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method154", "()D", false);
      mv.visitVarInsn(DSTORE, 64);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method127", "()D", false);
      mv.visitVarInsn(DSTORE, 66);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method155", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 68);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method156", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 69);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method157", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 70);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method158", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 71);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method159", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 72);
      mv.visitVarInsn(ALOAD, 69);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitLdcInsn("");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l2 = new Label();
      mv.visitJumpInsn(IFNE, l2);
      mv.visitVarInsn(ALOAD, 69);
      mv.visitInsn(ICONST_1);
      mv.visitMethodInsn(
          INVOKESTATIC, "ident124", "method160", "(Ljava/lang/String;Z)Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 69);
      mv.visitLabel(l2);
      mv.visitFrame(
          Opcodes.F_FULL,
          57,
          new Object[] {
            "Test",
            Opcodes.INTEGER,
            "java/lang/String",
            "java/lang/String",
            "java/lang/String",
            "java/lang/String",
            "java/lang/String",
            "java/lang/String",
            Opcodes.DOUBLE,
            "java/lang/String",
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            "java/lang/String",
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            "java/lang/String",
            "java/lang/String",
            Opcodes.DOUBLE,
            "java/lang/String",
            "java/lang/String",
            Opcodes.DOUBLE,
            Opcodes.LONG,
            Opcodes.LONG,
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            "java/lang/String",
            "java/lang/String",
            "java/lang/String",
            "java/lang/String",
            "java/lang/String",
            Opcodes.DOUBLE,
            Opcodes.DOUBLE,
            Opcodes.INTEGER,
            "java/lang/String",
            Opcodes.INTEGER,
            "java/lang/String",
            "ident138",
            "ident161",
            Opcodes.INTEGER,
            "java/lang/String",
            "ident132"
          },
          0,
          new Object[] {});
      mv.visitFieldInsn(GETSTATIC, "Test", "a", "Lident102;");
      mv.visitVarInsn(ALOAD, 2);
      mv.visitMethodInsn(
          INVOKESTATIC, "ident113", "method162", "(Lident102;Ljava/lang/String;)D", false);
      mv.visitInsn(ICONST_3);
      mv.visitMethodInsn(INVOKESTATIC, "ident124", "method126", "(DI)D", false);
      mv.visitVarInsn(DSTORE, 73);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method163", "()Ljava/lang/String;", false);
      mv.visitLdcInsn("S");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l3 = new Label();
      mv.visitJumpInsn(IFEQ, l3);
      mv.visitFieldInsn(GETSTATIC, "Test", "a", "Lident102;");
      mv.visitVarInsn(ALOAD, 2);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method164", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method165", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKESTATIC,
          "ident113",
          "method166",
          "(Lident102;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
          false);
      mv.visitVarInsn(ASTORE, 84);
      mv.visitLabel(l3);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFieldInsn(GETSTATIC, "Test", "a", "Lident102;");
      mv.visitVarInsn(ALOAD, 2);
      mv.visitFieldInsn(GETSTATIC, "Test", "s", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "q", "Ljava/lang/String;");
      mv.visitMethodInsn(
          INVOKESTATIC,
          "ident113",
          "method167",
          "(Lident102;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)D",
          false);
      mv.visitVarInsn(DSTORE, 75);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method134", "()Ljava/lang/String;", false);
      mv.visitLdcInsn("S");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l4 = new Label();
      mv.visitJumpInsn(IFEQ, l4);
      mv.visitVarInsn(ALOAD, 85);
      mv.visitJumpInsn(IFNULL, l4);
      mv.visitVarInsn(ALOAD, 85);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident132", "method133", "()Z", false);
      Label l5 = new Label();
      mv.visitJumpInsn(IFNE, l5);
      mv.visitLabel(l4);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, "Test", "v", "Ljava/lang/String;");
      mv.visitLdcInsn("L");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l6 = new Label();
      mv.visitJumpInsn(IFEQ, l6);
      mv.visitLabel(l5);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 2);
      mv.visitMethodInsn(INVOKESPECIAL, "Test", "a", "(Ljava/lang/String;)V", false);
      mv.visitLabel(l6);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method168", "()Ljava/lang/String;", false);
      mv.visitLdcInsn("S");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l7 = new Label();
      mv.visitJumpInsn(IFEQ, l7);
      mv.visitInsn(ICONST_1);
      mv.visitVarInsn(ISTORE, 79);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 2);
      mv.visitMethodInsn(
          INVOKESPECIAL, "Test", "c", "(Ljava/lang/String;)Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 80);
      mv.visitLabel(l7);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method169", "()D", false);
      mv.visitVarInsn(DSTORE, 48);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method170", "()D", false);
      mv.visitVarInsn(DSTORE, 50);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method171", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 52);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method172", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 53);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method173", "()D", false);
      mv.visitInsn(DCONST_0);
      mv.visitInsn(DCMPL);
      Label l8 = new Label();
      mv.visitJumpInsn(IFLE, l8);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method173", "()D", false);
      mv.visitVarInsn(DSTORE, 11);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method174", "()D", false);
      mv.visitVarInsn(DSTORE, 13);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method175", "()D", false);
      mv.visitVarInsn(DSTORE, 17);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method176", "()D", false);
      mv.visitVarInsn(DSTORE, 19);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method177", "()D", false);
      mv.visitVarInsn(DSTORE, 21);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method178", "()D", false);
      mv.visitVarInsn(DSTORE, 23);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method179", "()D", false);
      mv.visitVarInsn(DSTORE, 25);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method180", "()D", false);
      mv.visitVarInsn(DSTORE, 27);
      mv.visitFieldInsn(GETSTATIC, "Test", "g", "Lident109;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident109", "method181", "()D", false);
      mv.visitVarInsn(DSTORE, 32);
      mv.visitFieldInsn(GETSTATIC, "Test", "g", "Lident109;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident109", "method182", "()D", false);
      mv.visitVarInsn(DSTORE, 34);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method183", "()D", false);
      mv.visitVarInsn(DSTORE, 36);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method184", "()D", false);
      mv.visitVarInsn(DSTORE, 38);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method185", "()D", false);
      mv.visitVarInsn(DSTORE, 40);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method186", "()D", false);
      mv.visitVarInsn(DSTORE, 29);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method187", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 31);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method188", "()D", false);
      mv.visitVarInsn(DSTORE, 44);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method189", "()D", false);
      mv.visitVarInsn(DSTORE, 46);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method190", "()D", false);
      mv.visitVarInsn(DSTORE, 42);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method191", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 57);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method192", "()D", false);
      mv.visitVarInsn(DSTORE, 54);
      Label l9 = new Label();
      mv.visitJumpInsn(GOTO, l9);
      mv.visitLabel(l8);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitTypeInsn(NEW, "ident193");
      mv.visitInsn(DUP);
      mv.visitFieldInsn(GETSTATIC, "Test", "a", "Lident102;");
      mv.visitVarInsn(ALOAD, 2);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method194", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method195", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method196", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method197", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method198", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "k", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "o", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "n", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "m", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "l", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "g", "Lident109;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident109", "method199", "()Ljava/util/ArrayList;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "r", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "g", "Lident109;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident109", "method200", "()Ljava/util/ArrayList;", false);
      mv.visitVarInsn(DLOAD, 11);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method201", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method202", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "g", "Lident109;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident109", "method203", "()D", false);
      mv.visitMethodInsn(
          INVOKESPECIAL,
          "ident193",
          "<init>",
          "(Lident102;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/ArrayList;Ljava/lang/String;Ljava/util/ArrayList;DLjava/lang/String;Ljava/lang/String;D)V",
          false);
      mv.visitVarInsn(ASTORE, 86);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident193", "method204", "()D", false);
      mv.visitVarInsn(DSTORE, 13);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident193", "method205", "()D", false);
      mv.visitVarInsn(DSTORE, 17);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident193", "method206", "()D", false);
      mv.visitVarInsn(DSTORE, 19);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident193", "method207", "()D", false);
      mv.visitVarInsn(DSTORE, 21);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident193", "method208", "()D", false);
      mv.visitVarInsn(DSTORE, 23);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident193", "method209", "()D", false);
      mv.visitVarInsn(DSTORE, 25);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident193", "method210", "()D", false);
      mv.visitVarInsn(DSTORE, 27);
      mv.visitFieldInsn(GETSTATIC, "Test", "g", "Lident109;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident109", "method181", "()D", false);
      mv.visitVarInsn(DSTORE, 32);
      mv.visitFieldInsn(GETSTATIC, "Test", "g", "Lident109;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident109", "method182", "()D", false);
      mv.visitVarInsn(DSTORE, 34);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident193", "method211", "()D", false);
      mv.visitVarInsn(DSTORE, 36);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident193", "method212", "()D", false);
      mv.visitVarInsn(DSTORE, 38);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident193", "method213", "()D", false);
      mv.visitVarInsn(DSTORE, 40);
      mv.visitInsn(DCONST_0);
      mv.visitVarInsn(DSTORE, 29);
      mv.visitLdcInsn("");
      mv.visitVarInsn(ASTORE, 31);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident193", "method214", "()D", false);
      mv.visitVarInsn(DSTORE, 42);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident193", "method215", "()D", false);
      mv.visitVarInsn(DSTORE, 44);
      mv.visitVarInsn(DLOAD, 44);
      mv.visitInsn(DCONST_0);
      mv.visitInsn(DCMPL);
      Label l10 = new Label();
      mv.visitJumpInsn(IFLE, l10);
      mv.visitTypeInsn(NEW, "ident138");
      mv.visitInsn(DUP);
      mv.visitFieldInsn(GETSTATIC, "Test", "a", "Lident102;");
      mv.visitLdcInsn("tipo_um");
      mv.visitLdcInsn("anaart, tabum");
      mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
      mv.visitInsn(DUP);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
      mv.visitLdcInsn("anaart.cod_art = '");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      mv.visitVarInsn(ALOAD, 2);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      mv.visitLdcInsn("' AND anaart.cod_um = tabum.cod_um ");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
      mv.visitLdcInsn("");
      mv.visitMethodInsn(
          INVOKESPECIAL,
          "ident138",
          "<init>",
          "(Lident102;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
          false);
      mv.visitVarInsn(ASTORE, 81);
      mv.visitVarInsn(ALOAD, 81);
      mv.visitInsn(ICONST_0);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident138", "method139", "(I)Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 56);
      mv.visitVarInsn(ALOAD, 56);
      mv.visitLdcInsn("L");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l11 = new Label();
      mv.visitJumpInsn(IFEQ, l11);
      mv.visitVarInsn(DLOAD, 44);
      mv.visitInsn(ICONST_5);
      mv.visitMethodInsn(INVOKESTATIC, "ident124", "method126", "(DI)D", false);
      mv.visitVarInsn(DSTORE, 44);
      mv.visitLabel(l11);
      mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {"ident193"}, 0, null);
      mv.visitVarInsn(ALOAD, 56);
      mv.visitLdcInsn("C");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l12 = new Label();
      mv.visitJumpInsn(IFEQ, l12);
      mv.visitVarInsn(DLOAD, 44);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method152", "()J", false);
      mv.visitInsn(L2D);
      mv.visitInsn(DDIV);
      mv.visitInsn(ICONST_5);
      mv.visitMethodInsn(INVOKESTATIC, "ident124", "method126", "(DI)D", false);
      mv.visitVarInsn(DSTORE, 44);
      mv.visitLabel(l12);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 56);
      mv.visitLdcInsn("P");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l13 = new Label();
      mv.visitJumpInsn(IFEQ, l13);
      mv.visitVarInsn(DLOAD, 44);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method153", "()J", false);
      mv.visitInsn(L2D);
      mv.visitInsn(DDIV);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method152", "()J", false);
      mv.visitInsn(L2D);
      mv.visitInsn(DDIV);
      mv.visitInsn(ICONST_5);
      mv.visitMethodInsn(INVOKESTATIC, "ident124", "method126", "(DI)D", false);
      mv.visitVarInsn(DSTORE, 44);
      mv.visitLabel(l13);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 56);
      mv.visitLdcInsn("K");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      mv.visitJumpInsn(IFEQ, l10);
      mv.visitVarInsn(DLOAD, 44);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method154", "()D", false);
      mv.visitInsn(DDIV);
      mv.visitFieldInsn(GETSTATIC, "Test", "d", "Lident104;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident104", "method152", "()J", false);
      mv.visitInsn(L2D);
      mv.visitInsn(DDIV);
      mv.visitInsn(ICONST_5);
      mv.visitMethodInsn(INVOKESTATIC, "ident124", "method126", "(DI)D", false);
      mv.visitVarInsn(DSTORE, 44);
      mv.visitLabel(l10);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFieldInsn(GETSTATIC, "Test", "k", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "o", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "n", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "m", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "l", "Ljava/lang/String;");
      mv.visitVarInsn(ALOAD, 2);
      mv.visitMethodInsn(
          INVOKESTATIC,
          "Test",
          "a",
          "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z",
          false);
      mv.visitVarInsn(ISTORE, 77);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(ILOAD, 77);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method216", "(Z)V", false);
      mv.visitVarInsn(ILOAD, 77);
      Label l14 = new Label();
      mv.visitJumpInsn(IFEQ, l14);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method217", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitLdcInsn("");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      mv.visitJumpInsn(IFEQ, l14);
      mv.visitFieldInsn(GETSTATIC, "Test", "a", "Lident102;");
      mv.visitFieldInsn(GETSTATIC, "Test", "k", "Ljava/lang/String;");
      mv.visitVarInsn(ALOAD, 2);
      mv.visitMethodInsn(
          INVOKESTATIC,
          "ident113",
          "method218",
          "(Lident102;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
          false);
      mv.visitVarInsn(ASTORE, 78);
      mv.visitVarInsn(ALOAD, 78);
      mv.visitLdcInsn("");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l15 = new Label();
      mv.visitJumpInsn(IFEQ, l15);
      mv.visitFieldInsn(GETSTATIC, "Test", "a", "Lident102;");
      mv.visitFieldInsn(GETSTATIC, "Test", "o", "Ljava/lang/String;");
      mv.visitVarInsn(ALOAD, 2);
      mv.visitMethodInsn(
          INVOKESTATIC,
          "ident113",
          "method218",
          "(Lident102;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
          false);
      mv.visitVarInsn(ASTORE, 78);
      mv.visitLabel(l15);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 78);
      mv.visitLdcInsn("");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l16 = new Label();
      mv.visitJumpInsn(IFEQ, l16);
      mv.visitFieldInsn(GETSTATIC, "Test", "a", "Lident102;");
      mv.visitFieldInsn(GETSTATIC, "Test", "n", "Ljava/lang/String;");
      mv.visitVarInsn(ALOAD, 2);
      mv.visitMethodInsn(
          INVOKESTATIC,
          "ident113",
          "method218",
          "(Lident102;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
          false);
      mv.visitVarInsn(ASTORE, 78);
      mv.visitLabel(l16);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 78);
      mv.visitLdcInsn("");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l17 = new Label();
      mv.visitJumpInsn(IFEQ, l17);
      mv.visitFieldInsn(GETSTATIC, "Test", "a", "Lident102;");
      mv.visitFieldInsn(GETSTATIC, "Test", "m", "Ljava/lang/String;");
      mv.visitVarInsn(ALOAD, 2);
      mv.visitMethodInsn(
          INVOKESTATIC,
          "ident113",
          "method218",
          "(Lident102;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
          false);
      mv.visitVarInsn(ASTORE, 78);
      mv.visitLabel(l17);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 78);
      mv.visitLdcInsn("");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l18 = new Label();
      mv.visitJumpInsn(IFEQ, l18);
      mv.visitFieldInsn(GETSTATIC, "Test", "a", "Lident102;");
      mv.visitFieldInsn(GETSTATIC, "Test", "l", "Ljava/lang/String;");
      mv.visitVarInsn(ALOAD, 2);
      mv.visitMethodInsn(
          INVOKESTATIC,
          "ident113",
          "method218",
          "(Lident102;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
          false);
      mv.visitVarInsn(ASTORE, 78);
      mv.visitLabel(l18);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 78);
      mv.visitLdcInsn("");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      mv.visitJumpInsn(IFNE, l14);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(ALOAD, 78);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method219", "(Ljava/lang/String;)V", false);
      mv.visitLabel(l14);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 13);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method220", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 17);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method221", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 19);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method222", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 21);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method223", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 23);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method224", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 25);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method225", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 27);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method226", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 29);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method227", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 36);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method228", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 38);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method229", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 40);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method230", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 42);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method231", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 13);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method232", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 44);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method233", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 17);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method234", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 19);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method235", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 21);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method236", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 23);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method237", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 25);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method238", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 27);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method239", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 29);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method240", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 36);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method241", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 38);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method242", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 40);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method243", "(D)V", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "t", "Ljava/lang/String;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitLdcInsn("");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l19 = new Label();
      mv.visitJumpInsn(IFNE, l19);
      mv.visitFieldInsn(GETSTATIC, "Test", "t", "Ljava/lang/String;");
      mv.visitVarInsn(ASTORE, 57);
      mv.visitLabel(l19);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitTypeInsn(NEW, "ident244");
      mv.visitInsn(DUP);
      mv.visitFieldInsn(GETSTATIC, "Test", "a", "Lident102;");
      mv.visitVarInsn(ALOAD, 2);
      mv.visitFieldInsn(GETSTATIC, "Test", "k", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "o", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "n", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "m", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "l", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "r", "Ljava/lang/String;");
      mv.visitMethodInsn(
          INVOKESPECIAL,
          "ident244",
          "<init>",
          "(Lident102;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
          false);
      mv.visitVarInsn(ASTORE, 87);
      mv.visitVarInsn(ALOAD, 87);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident244", "method245", "()D", false);
      mv.visitVarInsn(DSTORE, 54);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitVarInsn(DLOAD, 54);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method246", "(D)V", false);
      mv.visitLabel(l9);
      mv.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
      mv.visitVarInsn(DLOAD, 13);
      mv.visitVarInsn(DSTORE, 15);
      mv.visitVarInsn(DLOAD, 15);
      mv.visitInsn(DCONST_1);
      mv.visitVarInsn(DLOAD, 17);
      mv.visitLdcInsn(new Double("100.0"));
      mv.visitInsn(DDIV);
      mv.visitInsn(DSUB);
      mv.visitInsn(DMUL);
      mv.visitVarInsn(DSTORE, 15);
      mv.visitVarInsn(DLOAD, 15);
      mv.visitInsn(DCONST_1);
      mv.visitVarInsn(DLOAD, 19);
      mv.visitLdcInsn(new Double("100.0"));
      mv.visitInsn(DDIV);
      mv.visitInsn(DSUB);
      mv.visitInsn(DMUL);
      mv.visitVarInsn(DSTORE, 15);
      mv.visitVarInsn(DLOAD, 15);
      mv.visitInsn(DCONST_1);
      mv.visitVarInsn(DLOAD, 29);
      mv.visitLdcInsn(new Double("100.0"));
      mv.visitInsn(DDIV);
      mv.visitInsn(DSUB);
      mv.visitInsn(DMUL);
      mv.visitVarInsn(DSTORE, 15);
      mv.visitVarInsn(DLOAD, 15);
      mv.visitInsn(DCONST_1);
      mv.visitVarInsn(DLOAD, 21);
      mv.visitLdcInsn(new Double("100.0"));
      mv.visitInsn(DDIV);
      mv.visitInsn(DSUB);
      mv.visitInsn(DMUL);
      mv.visitVarInsn(DSTORE, 15);
      mv.visitVarInsn(DLOAD, 15);
      mv.visitInsn(DCONST_1);
      mv.visitVarInsn(DLOAD, 23);
      mv.visitLdcInsn(new Double("100.0"));
      mv.visitInsn(DDIV);
      mv.visitInsn(DSUB);
      mv.visitInsn(DMUL);
      mv.visitVarInsn(DSTORE, 15);
      mv.visitVarInsn(DLOAD, 15);
      mv.visitInsn(DCONST_1);
      mv.visitVarInsn(DLOAD, 25);
      mv.visitLdcInsn(new Double("100.0"));
      mv.visitInsn(DDIV);
      mv.visitInsn(DSUB);
      mv.visitInsn(DMUL);
      mv.visitVarInsn(DSTORE, 15);
      mv.visitVarInsn(DLOAD, 15);
      mv.visitInsn(DCONST_1);
      mv.visitVarInsn(DLOAD, 27);
      mv.visitLdcInsn(new Double("100.0"));
      mv.visitInsn(DDIV);
      mv.visitInsn(DSUB);
      mv.visitInsn(DMUL);
      mv.visitVarInsn(DSTORE, 15);
      mv.visitVarInsn(DLOAD, 15);
      mv.visitInsn(DCONST_1);
      mv.visitVarInsn(DLOAD, 32);
      mv.visitLdcInsn(new Double("100.0"));
      mv.visitInsn(DDIV);
      mv.visitInsn(DSUB);
      mv.visitInsn(DMUL);
      mv.visitVarInsn(DSTORE, 15);
      mv.visitVarInsn(DLOAD, 15);
      mv.visitInsn(DCONST_1);
      mv.visitVarInsn(DLOAD, 34);
      mv.visitLdcInsn(new Double("100.0"));
      mv.visitInsn(DDIV);
      mv.visitInsn(DSUB);
      mv.visitInsn(DMUL);
      mv.visitVarInsn(DSTORE, 15);
      mv.visitVarInsn(DLOAD, 15);
      mv.visitInsn(ICONST_5);
      mv.visitMethodInsn(INVOKESTATIC, "ident124", "method126", "(DI)D", false);
      mv.visitVarInsn(DSTORE, 15);
      mv.visitVarInsn(DLOAD, 15);
      mv.visitVarInsn(DLOAD, 11);
      mv.visitInsn(DMUL);
      mv.visitInsn(ICONST_2);
      mv.visitMethodInsn(INVOKESTATIC, "ident124", "method126", "(DI)D", false);
      mv.visitVarInsn(DSTORE, 46);
      mv.visitTypeInsn(NEW, "ident161");
      mv.visitInsn(DUP);
      mv.visitMethodInsn(INVOKESPECIAL, "ident161", "<init>", "()V", false);
      mv.visitVarInsn(ASTORE, 82);
      mv.visitFieldInsn(GETSTATIC, "Test", "a", "Lident102;");
      mv.visitFieldInsn(GETSTATIC, "Test", "k", "Ljava/lang/String;");
      mv.visitVarInsn(ALOAD, 2);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method247", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(
          INVOKESTATIC,
          "ident113",
          "method248",
          "(Lident102;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lident161;)V",
          false);
      mv.visitJumpInsn(GOTO, l0);
      mv.visitLabel(l1);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitInsn(ICONST_M1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method249", "(I)V", false);
      mv.visitInsn(ACONST_NULL);
      mv.visitVarInsn(ASTORE, 82);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "Test", "b", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitLabel(l0);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 85);
      Label l20 = new Label();
      mv.visitJumpInsn(IFNULL, l20);
      mv.visitVarInsn(ALOAD, 85);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident132", "method133", "()Z", false);
      mv.visitJumpInsn(IFEQ, l20);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, "Test", "v", "Ljava/lang/String;");
      mv.visitLdcInsn("P");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l21 = new Label();
      mv.visitJumpInsn(IFEQ, l21);
      mv.visitLabel(l20);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ILOAD, 1);
      mv.visitJumpInsn(IFLT, l21);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method146", "()I", false);
      mv.visitJumpInsn(IFLE, l21);
      mv.visitTypeInsn(NEW, "android/content/Intent");
      mv.visitInsn(DUP);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKEVIRTUAL, "Test", "method250", "()Lident1234;", false);
      mv.visitLdcInsn(Type.getType("Lident251;"));
      mv.visitMethodInsn(
          INVOKESPECIAL,
          "android/content/Intent",
          "<init>",
          "(Landroid/content/Context;Ljava/lang/Class;)V",
          false);
      mv.visitVarInsn(ASTORE, 86);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method149", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 2);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method252", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 3);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method253", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 4);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method254", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method255", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 6);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method256", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 7);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method257", "()Z", false);
      mv.visitVarInsn(ISTORE, 77);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method217", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 78);
      mv.visitTypeInsn(NEW, "ident138");
      mv.visitInsn(DUP);
      mv.visitFieldInsn(GETSTATIC, "Test", "a", "Lident102;");
      mv.visitLdcInsn("aliquota");
      mv.visitLdcInsn(" tabiva ");
      mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
      mv.visitInsn(DUP);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
      mv.visitLdcInsn(" cod_iva = '");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      mv.visitVarInsn(ALOAD, 57);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      mv.visitLdcInsn("' ");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
      mv.visitLdcInsn("");
      mv.visitMethodInsn(
          INVOKESPECIAL,
          "ident138",
          "<init>",
          "(Lident102;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
          false);
      mv.visitVarInsn(ASTORE, 81);
      mv.visitVarInsn(ALOAD, 81);
      mv.visitInsn(ICONST_0);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident138", "method139", "(I)Ljava/lang/String;", false);
      mv.visitInsn(DCONST_0);
      mv.visitMethodInsn(INVOKESTATIC, "ident124", "method125", "(Ljava/lang/String;D)D", false);
      mv.visitVarInsn(DSTORE, 58);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method173", "()D", false);
      mv.visitInsn(DCONST_0);
      mv.visitInsn(DCMPL);
      Label l22 = new Label();
      mv.visitJumpInsn(IFLE, l22);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method258", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitLdcInsn("0");
      mv.visitMethodInsn(
          INVOKESTATIC,
          "ident124",
          "method140",
          "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
          false);
      mv.visitVarInsn(ASTORE, 10);
      mv.visitVarInsn(ALOAD, 10);
      mv.visitLdcInsn(".");
      mv.visitLdcInsn("");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/String",
          "replace",
          "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;",
          false);
      mv.visitVarInsn(ASTORE, 10);
      mv.visitVarInsn(ALOAD, 10);
      mv.visitLdcInsn(",");
      mv.visitLdcInsn(".");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/String",
          "replace",
          "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;",
          false);
      mv.visitVarInsn(ASTORE, 10);
      mv.visitVarInsn(ALOAD, 10);
      mv.visitMethodInsn(
          INVOKESTATIC, "java/lang/Double", "parseDouble", "(Ljava/lang/String;)D", false);
      mv.visitVarInsn(DSTORE, 8);
      Label l23 = new Label();
      mv.visitJumpInsn(GOTO, l23);
      mv.visitLabel(l22);
      mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {"android/content/Intent"}, 0, null);
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method259", "()Ljava/lang/String;", false);
      mv.visitLdcInsn("O");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l24 = new Label();
      mv.visitJumpInsn(IFEQ, l24);
      mv.visitVarInsn(ALOAD, 70);
      mv.visitLdcInsn("L");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l25 = new Label();
      mv.visitJumpInsn(IFEQ, l25);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method260", "()D", false);
      mv.visitVarInsn(DSTORE, 8);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method261", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      Label l26 = new Label();
      mv.visitJumpInsn(GOTO, l26);
      mv.visitLabel(l25);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 70);
      mv.visitLdcInsn("C");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l27 = new Label();
      mv.visitJumpInsn(IFEQ, l27);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method262", "()D", false);
      mv.visitVarInsn(DSTORE, 8);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method263", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitJumpInsn(GOTO, l26);
      mv.visitLabel(l27);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 70);
      mv.visitLdcInsn("P");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l28 = new Label();
      mv.visitJumpInsn(IFEQ, l28);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method264", "()D", false);
      mv.visitVarInsn(DSTORE, 8);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method265", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitJumpInsn(GOTO, l26);
      mv.visitLabel(l28);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 70);
      mv.visitLdcInsn("K");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l29 = new Label();
      mv.visitJumpInsn(IFEQ, l29);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method266", "()D", false);
      mv.visitVarInsn(DSTORE, 8);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method267", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitJumpInsn(GOTO, l26);
      mv.visitLabel(l29);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method260", "()D", false);
      mv.visitVarInsn(DSTORE, 8);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method261", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitLabel(l26);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(DLOAD, 8);
      mv.visitInsn(DCONST_0);
      mv.visitInsn(DCMPL);
      mv.visitJumpInsn(IFLE, l23);
      mv.visitInsn(ICONST_1);
      mv.visitVarInsn(ISTORE, 83);
      mv.visitJumpInsn(GOTO, l23);
      mv.visitLabel(l24);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method259", "()Ljava/lang/String;", false);
      mv.visitLdcInsn("U");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      mv.visitJumpInsn(IFEQ, l23);
      mv.visitVarInsn(ALOAD, 70);
      mv.visitLdcInsn("L");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l30 = new Label();
      mv.visitJumpInsn(IFEQ, l30);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method261", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      Label l31 = new Label();
      mv.visitJumpInsn(GOTO, l31);
      mv.visitLabel(l30);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 70);
      mv.visitLdcInsn("C");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l32 = new Label();
      mv.visitJumpInsn(IFEQ, l32);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method263", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitJumpInsn(GOTO, l31);
      mv.visitLabel(l32);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 70);
      mv.visitLdcInsn("P");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l33 = new Label();
      mv.visitJumpInsn(IFEQ, l33);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method265", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitJumpInsn(GOTO, l31);
      mv.visitLabel(l33);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 70);
      mv.visitLdcInsn("K");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l34 = new Label();
      mv.visitJumpInsn(IFEQ, l34);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method267", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitJumpInsn(GOTO, l31);
      mv.visitLabel(l34);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method261", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitLabel(l31);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitInsn(DCONST_1);
      mv.visitVarInsn(DSTORE, 8);
      mv.visitInsn(ICONST_1);
      mv.visitVarInsn(ISTORE, 83);
      mv.visitLabel(l23);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("orientation");
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, "Test", "v", "Ljava/lang/String;");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("index");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;I)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("visualizza_ordine");
      mv.visitFieldInsn(GETSTATIC, "Test", "p", "Z");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Z)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("cod_cli");
      mv.visitFieldInsn(GETSTATIC, "Test", "k", "Ljava/lang/String;");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("cod_ins");
      mv.visitFieldInsn(GETSTATIC, "Test", "o", "Ljava/lang/String;");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("cod_int");
      mv.visitFieldInsn(GETSTATIC, "Test", "n", "Ljava/lang/String;");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("cod_grp");
      mv.visitFieldInsn(GETSTATIC, "Test", "m", "Ljava/lang/String;");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("cod_gda");
      mv.visitFieldInsn(GETSTATIC, "Test", "l", "Ljava/lang/String;");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("cod_art");
      mv.visitVarInsn(ALOAD, 2);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("des_art");
      mv.visitVarInsn(ALOAD, 3);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("prezzo_unitario");
      mv.visitVarInsn(DLOAD, 13);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("prezzo_netto");
      mv.visitVarInsn(DLOAD, 15);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("importo");
      mv.visitVarInsn(DLOAD, 46);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_cliart");
      mv.visitVarInsn(DLOAD, 17);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_listino");
      mv.visitVarInsn(DLOAD, 19);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_incondiz1");
      mv.visitVarInsn(DLOAD, 21);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_incondiz2");
      mv.visitVarInsn(DLOAD, 23);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_incondiz3");
      mv.visitVarInsn(DLOAD, 25);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_promozione");
      mv.visitVarInsn(DLOAD, 29);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("cod_prom");
      mv.visitVarInsn(ALOAD, 31);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_fine_anno");
      mv.visitVarInsn(DLOAD, 27);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_pag1");
      mv.visitVarInsn(DLOAD, 32);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_pag2");
      mv.visitVarInsn(DLOAD, 34);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_valore1");
      mv.visitVarInsn(DLOAD, 36);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_valore2");
      mv.visitVarInsn(DLOAD, 38);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_valore3");
      mv.visitVarInsn(DLOAD, 40);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("cod_um");
      mv.visitVarInsn(ALOAD, 4);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("qta_ord");
      mv.visitVarInsn(DLOAD, 8);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("um_ord");
      mv.visitVarInsn(ALOAD, 5);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("tipo_omaggio");
      mv.visitVarInsn(ALOAD, 6);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("tipo_um_ordcli");
      mv.visitVarInsn(ALOAD, 70);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("des_stato");
      mv.visitVarInsn(ALOAD, 68);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("data_stato");
      mv.visitVarInsn(ALOAD, 69);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("flag_bloccato");
      mv.visitVarInsn(ALOAD, 71);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("flag_novita");
      mv.visitVarInsn(ALOAD, 72);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("giacenza");
      mv.visitVarInsn(DLOAD, 73);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("prezzo_listino_base");
      mv.visitVarInsn(DLOAD, 75);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("commento");
      mv.visitVarInsn(ALOAD, 7);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("aliquota");
      mv.visitVarInsn(DLOAD, 58);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("pezzi_conf");
      mv.visitVarInsn(LLOAD, 62);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;J)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("conf_collo");
      mv.visitVarInsn(LLOAD, 60);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;J)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("peso_netto_conf");
      mv.visitVarInsn(DLOAD, 64);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("flag_vis_margine");
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method268", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("flag_disabil_sm");
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method269", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("flag_disabil_ic");
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method270", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("flag_disabil_id");
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method271", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("flag_disabil_sos");
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method272", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("convqta");
      mv.visitVarInsn(ILOAD, 77);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Z)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("desconvqta");
      mv.visitVarInsn(ALOAD, 78);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("um_co_eti");
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method261", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("um_cf_eti");
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method263", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("um_pz_eti");
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method265", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("um_pe_eti");
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method267", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("prezzo_uni_orig");
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method273", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_cli_orig");
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method274", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_lis_orig");
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method275", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_inc1_orig");
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method276", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_inc2_orig");
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method277", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_inc3_orig");
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method278", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_promo_orig");
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method279", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_fine_orig");
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method280", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_pag1_orig");
      mv.visitVarInsn(DLOAD, 32);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_pag2_orig");
      mv.visitVarInsn(DLOAD, 34);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_val1_orig");
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method281", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_val2_orig");
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method282", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sco_val3_orig");
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method283", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("prezzo_collo_orig");
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method188", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("flag_prz_mano");
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method284", "()Z", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Z)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("data_ricerca");
      mv.visitFieldInsn(GETSTATIC, "Test", "r", "Ljava/lang/String;");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("data_ult_ord");
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method285", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("prezzo_ult_ord");
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method286", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("prezzo_lis_ult_ord");
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method287", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("co_ult_ord");
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method260", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("cf_ult_ord");
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method262", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("pz_ult_ord");
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method264", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("pe_ult_ord");
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method266", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("qta_ult_ord");
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method288", "()D", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("cod_lis_val");
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method289", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("art_in_promo");
      mv.visitVarInsn(ILOAD, 79);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Z)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("des_art_in_promo");
      mv.visitVarInsn(ALOAD, 80);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("flag_apppromo");
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method290", "()Z", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Z)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("img_web");
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method291", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("flag_prezzi");
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method292", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("prezzo_pers");
      mv.visitVarInsn(DLOAD, 48);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("sconto_pers");
      mv.visitVarInsn(DLOAD, 50);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("da_data_pers");
      mv.visitVarInsn(ALOAD, 52);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("a_data_pers");
      mv.visitVarInsn(ALOAD, 53);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("data_ord");
      mv.visitFieldInsn(GETSTATIC, "Test", "q", "Ljava/lang/String;");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("flag_qta_prop");
      mv.visitVarInsn(ILOAD, 83);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Z)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("flag_depositi");
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method163", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("giacenza_depositi");
      mv.visitVarInsn(ALOAD, 84);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitLdcInsn("ord_prezzo_cli");
      mv.visitVarInsn(DLOAD, 54);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/content/Intent",
          "putExtra",
          "(Ljava/lang/String;D)Landroid/content/Intent;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitInsn(ICONST_0);
      mv.visitFieldInsn(PUTFIELD, "Test", "N", "Z");
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 86);
      mv.visitInsn(ICONST_0);
      mv.visitMethodInsn(INVOKEVIRTUAL, "Test", "method293", "(Landroid/content/Intent;I)V", false);
      Label l35 = new Label();
      mv.visitJumpInsn(GOTO, l35);
      mv.visitLabel(l21);
      mv.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method146", "()I", false);
      mv.visitJumpInsn(IFLE, l35);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method149", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 2);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method252", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 3);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method253", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 4);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method254", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method255", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 6);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method256", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 7);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method257", "()Z", false);
      mv.visitVarInsn(ISTORE, 77);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method217", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 78);
      mv.visitTypeInsn(NEW, "ident138");
      mv.visitInsn(DUP);
      mv.visitFieldInsn(GETSTATIC, "Test", "a", "Lident102;");
      mv.visitLdcInsn("aliquota");
      mv.visitLdcInsn(" tabiva ");
      mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
      mv.visitInsn(DUP);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
      mv.visitLdcInsn(" cod_iva = '");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      mv.visitVarInsn(ALOAD, 57);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      mv.visitLdcInsn("' ");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
      mv.visitLdcInsn("");
      mv.visitMethodInsn(
          INVOKESPECIAL,
          "ident138",
          "<init>",
          "(Lident102;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
          false);
      mv.visitVarInsn(ASTORE, 81);
      mv.visitVarInsn(ALOAD, 81);
      mv.visitInsn(ICONST_0);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident138", "method139", "(I)Ljava/lang/String;", false);
      mv.visitInsn(DCONST_0);
      mv.visitMethodInsn(INVOKESTATIC, "ident124", "method125", "(Ljava/lang/String;D)D", false);
      mv.visitVarInsn(DSTORE, 58);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method173", "()D", false);
      mv.visitInsn(DCONST_0);
      mv.visitInsn(DCMPL);
      Label l36 = new Label();
      mv.visitJumpInsn(IFLE, l36);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method258", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitLdcInsn("0");
      mv.visitMethodInsn(
          INVOKESTATIC,
          "ident124",
          "method140",
          "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
          false);
      mv.visitVarInsn(ASTORE, 10);
      mv.visitVarInsn(ALOAD, 10);
      mv.visitLdcInsn(".");
      mv.visitLdcInsn("");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/String",
          "replace",
          "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;",
          false);
      mv.visitVarInsn(ASTORE, 10);
      mv.visitVarInsn(ALOAD, 10);
      mv.visitLdcInsn(",");
      mv.visitLdcInsn(".");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/String",
          "replace",
          "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;",
          false);
      mv.visitVarInsn(ASTORE, 10);
      mv.visitVarInsn(ALOAD, 10);
      mv.visitMethodInsn(
          INVOKESTATIC, "java/lang/Double", "parseDouble", "(Ljava/lang/String;)D", false);
      mv.visitVarInsn(DSTORE, 8);
      Label l37 = new Label();
      mv.visitJumpInsn(GOTO, l37);
      mv.visitLabel(l36);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method259", "()Ljava/lang/String;", false);
      mv.visitLdcInsn("O");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l38 = new Label();
      mv.visitJumpInsn(IFEQ, l38);
      mv.visitVarInsn(ALOAD, 70);
      mv.visitLdcInsn("L");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l39 = new Label();
      mv.visitJumpInsn(IFEQ, l39);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method260", "()D", false);
      mv.visitVarInsn(DSTORE, 8);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method261", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      Label l40 = new Label();
      mv.visitJumpInsn(GOTO, l40);
      mv.visitLabel(l39);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 70);
      mv.visitLdcInsn("C");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l41 = new Label();
      mv.visitJumpInsn(IFEQ, l41);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method262", "()D", false);
      mv.visitVarInsn(DSTORE, 8);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method263", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitJumpInsn(GOTO, l40);
      mv.visitLabel(l41);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 70);
      mv.visitLdcInsn("P");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l42 = new Label();
      mv.visitJumpInsn(IFEQ, l42);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method264", "()D", false);
      mv.visitVarInsn(DSTORE, 8);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method265", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitJumpInsn(GOTO, l40);
      mv.visitLabel(l42);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 70);
      mv.visitLdcInsn("K");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l43 = new Label();
      mv.visitJumpInsn(IFEQ, l43);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method266", "()D", false);
      mv.visitVarInsn(DSTORE, 8);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method267", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitJumpInsn(GOTO, l40);
      mv.visitLabel(l43);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method260", "()D", false);
      mv.visitVarInsn(DSTORE, 8);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method261", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitLabel(l40);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(DLOAD, 8);
      mv.visitInsn(DCONST_0);
      mv.visitInsn(DCMPL);
      mv.visitJumpInsn(IFLE, l37);
      mv.visitInsn(ICONST_1);
      mv.visitVarInsn(ISTORE, 83);
      mv.visitJumpInsn(GOTO, l37);
      mv.visitLabel(l38);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method259", "()Ljava/lang/String;", false);
      mv.visitLdcInsn("U");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      mv.visitJumpInsn(IFEQ, l37);
      mv.visitInsn(DCONST_1);
      mv.visitVarInsn(DSTORE, 8);
      mv.visitVarInsn(ALOAD, 70);
      mv.visitLdcInsn("L");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l44 = new Label();
      mv.visitJumpInsn(IFEQ, l44);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method261", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      Label l45 = new Label();
      mv.visitJumpInsn(GOTO, l45);
      mv.visitLabel(l44);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 70);
      mv.visitLdcInsn("C");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l46 = new Label();
      mv.visitJumpInsn(IFEQ, l46);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method263", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitJumpInsn(GOTO, l45);
      mv.visitLabel(l46);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 70);
      mv.visitLdcInsn("P");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l47 = new Label();
      mv.visitJumpInsn(IFEQ, l47);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method265", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitJumpInsn(GOTO, l45);
      mv.visitLabel(l47);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 70);
      mv.visitLdcInsn("K");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      Label l48 = new Label();
      mv.visitJumpInsn(IFEQ, l48);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method267", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitJumpInsn(GOTO, l45);
      mv.visitLabel(l48);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method261", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitLabel(l45);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitInsn(ICONST_1);
      mv.visitVarInsn(ISTORE, 83);
      mv.visitLabel(l37);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitInsn(ICONST_1);
      mv.visitFieldInsn(PUTFIELD, "Test", "M", "Z");
      mv.visitVarInsn(ALOAD, 85);
      mv.visitVarInsn(ILOAD, 1);
      mv.visitFieldInsn(GETSTATIC, "Test", "p", "Z");
      mv.visitFieldInsn(GETSTATIC, "Test", "k", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "o", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "n", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "m", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "l", "Ljava/lang/String;");
      mv.visitVarInsn(ALOAD, 2);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitVarInsn(DLOAD, 13);
      mv.visitVarInsn(DLOAD, 17);
      mv.visitVarInsn(DLOAD, 19);
      mv.visitVarInsn(DLOAD, 21);
      mv.visitVarInsn(DLOAD, 23);
      mv.visitVarInsn(DLOAD, 25);
      mv.visitVarInsn(DLOAD, 29);
      mv.visitVarInsn(ALOAD, 31);
      mv.visitVarInsn(DLOAD, 27);
      mv.visitVarInsn(DLOAD, 32);
      mv.visitVarInsn(DLOAD, 34);
      mv.visitVarInsn(DLOAD, 36);
      mv.visitVarInsn(DLOAD, 38);
      mv.visitVarInsn(DLOAD, 40);
      mv.visitVarInsn(DLOAD, 15);
      mv.visitVarInsn(DLOAD, 46);
      mv.visitVarInsn(ALOAD, 4);
      mv.visitVarInsn(ALOAD, 5);
      mv.visitVarInsn(DLOAD, 8);
      mv.visitVarInsn(ALOAD, 6);
      mv.visitVarInsn(ALOAD, 70);
      mv.visitVarInsn(ALOAD, 68);
      mv.visitVarInsn(ALOAD, 69);
      mv.visitVarInsn(DLOAD, 73);
      mv.visitVarInsn(DLOAD, 75);
      mv.visitVarInsn(ALOAD, 7);
      mv.visitVarInsn(ALOAD, 71);
      mv.visitVarInsn(ALOAD, 72);
      mv.visitVarInsn(DLOAD, 58);
      mv.visitVarInsn(LLOAD, 62);
      mv.visitVarInsn(LLOAD, 60);
      mv.visitVarInsn(DLOAD, 64);
      mv.visitVarInsn(ILOAD, 77);
      mv.visitVarInsn(ALOAD, 78);
      mv.visitInsn(DCONST_0);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method261", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method263", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method265", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method267", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method273", "()D", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method274", "()D", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method275", "()D", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method276", "()D", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method277", "()D", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method278", "()D", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method279", "()D", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method280", "()D", false);
      mv.visitVarInsn(DLOAD, 32);
      mv.visitVarInsn(DLOAD, 34);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method281", "()D", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method282", "()D", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method283", "()D", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method188", "()D", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method134", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method285", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method286", "()D", false);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method287", "()D", false);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method260", "()D", false);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method262", "()D", false);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method264", "()D", false);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method266", "()D", false);
      mv.visitVarInsn(ALOAD, 82);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident161", "method288", "()D", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method284", "()Z", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method294", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method295", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method296", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method297", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method298", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method299", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method300", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method301", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method302", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method303", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method304", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method289", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ILOAD, 79);
      mv.visitVarInsn(ALOAD, 80);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method290", "()Z", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method247", "()Ljava/lang/String;", false);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method165", "()Ljava/lang/String;", false);
      mv.visitVarInsn(DLOAD, 66);
      mv.visitFieldInsn(GETSTATIC, "Test", "b", "Lident103;");
      mv.visitVarInsn(ILOAD, 1);
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident103", "method147", "(I)Ljava/lang/Object;", false);
      mv.visitTypeInsn(CHECKCAST, "ident148");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident148", "method291", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitVarInsn(DLOAD, 48);
      mv.visitVarInsn(DLOAD, 50);
      mv.visitVarInsn(ALOAD, 52);
      mv.visitVarInsn(ALOAD, 53);
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method305", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ILOAD, 83);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method163", "()Ljava/lang/String;", false);
      mv.visitVarInsn(ALOAD, 84);
      mv.visitFieldInsn(GETSTATIC, "Test", "e", "Lident106;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident106", "method164", "()Ljava/lang/String;", false);
      mv.visitVarInsn(DLOAD, 54);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, "Test", "v", "Ljava/lang/String;");
      mv.visitFieldInsn(GETSTATIC, "Test", "f", "Lident107;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "ident107", "method135", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "ident132",
          "method136",
          "(IZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;DDDDDDDLjava/lang/String;DDDDDDDDLjava/lang/String;Ljava/lang/String;DLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;DDLjava/lang/String;Ljava/lang/String;Ljava/lang/String;DJJDZLjava/lang/String;DLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;DDDDDDDDDDDDDDLjava/lang/String;Ljava/lang/String;DDDDDDDZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZLjava/lang/String;ZLjava/lang/String;Ljava/lang/String;DLjava/lang/String;DDLjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;DLjava/lang/String;Ljava/lang/String;)V",
          false);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "Test", "a", "()V", false);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitInsn(ICONST_0);
      mv.visitFieldInsn(PUTFIELD, "Test", "M", "Z");
      mv.visitFieldInsn(GETSTATIC, "Test", "p", "Z");
      mv.visitJumpInsn(IFNE, l35);
      mv.visitVarInsn(ALOAD, 71);
      mv.visitLdcInsn("S");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
      mv.visitJumpInsn(IFNE, l35);
      mv.visitFieldInsn(GETSTATIC, "Test", "i", "Landroid/view/inputmethod/InputMethodManager;");
      mv.visitInsn(ICONST_2);
      mv.visitInsn(ICONST_1);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "android/view/inputmethod/InputMethodManager",
          "toggleSoftInput",
          "(II)V",
          false);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, "Test", "w", "Landroid/widget/EditText;");
      mv.visitMethodInsn(INVOKEVIRTUAL, "android/widget/EditText", "selectAll", "()V", false);
      mv.visitLabel(l35);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitInsn(RETURN);
      mv.visitMaxs(152, 88);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }
}
