// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b77842465;

import com.android.tools.r8.utils.DescriptorUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class Regress77842465Dump implements Opcodes {

  public static final String CLASS_NAME = "a.a";
  public static final String CLASS_DESC = DescriptorUtils.javaTypeToDescriptor(CLASS_NAME);
  public static final String CLASS_INTERNAL = DescriptorUtils.descriptorToInternalName(CLASS_DESC);

  public static byte[] dump() {

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;

    cw.visit(
        V1_7, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, CLASS_INTERNAL, null, "java/lang/Object", null);

    {
      fv = cw.visitField(0, "b", "I", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "c", "Ljava/lang/String;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "d", "Ljava/lang/String;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "g", "J", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(0, "h", "J", null, null);
      fv.visitEnd();
    }

    {
      mv = cw.visitMethod(ACC_PUBLIC + ACC_FINAL, "a",
          "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;IJ)" + CLASS_DESC,
          null, null);
      mv.visitCode();
      Label l0 = new Label();
      Label l1 = new Label();
      Label l2 = new Label();
      mv.visitTryCatchBlock(l0, l1, l2, "java/lang/Exception");
      Label l3 = new Label();
      Label l4 = new Label();
      Label l5 = new Label();
      mv.visitTryCatchBlock(l3, l4, l5, "java/lang/Exception");
      mv.visitVarInsn(ALOAD, 3);
      mv.visitFieldInsn(GETFIELD, "java/lang/Object", "c", "Ljava/util/Map;");
      mv.visitVarInsn(ASTORE, 3);
      mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
      mv.visitVarInsn(LSTORE, 8);
      mv.visitInsn(LCONST_0);
      mv.visitVarInsn(LSTORE, 10);
      mv.visitInsn(LCONST_0);
      mv.visitVarInsn(LSTORE, 12);
      mv.visitInsn(LCONST_0);
      mv.visitVarInsn(LSTORE, 14);
      mv.visitInsn(LCONST_0);
      mv.visitVarInsn(LSTORE, 16);
      mv.visitInsn(LCONST_0);
      mv.visitVarInsn(LSTORE, 18);
      mv.visitInsn(LCONST_0);
      mv.visitVarInsn(LSTORE, 20);
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ISTORE, 7);
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ISTORE, 22);
      mv.visitTypeInsn(NEW, "java/util/ArrayList");
      mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitLdcInsn("Date");
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get",
          "(Ljava/lang/Object;)Ljava/lang/Object;", true);
      mv.visitTypeInsn(CHECKCAST, "java/util/List");
      mv.visitInsn(DUP);
      mv.visitVarInsn(ASTORE, 23);
      Label l6 = new Label();
      mv.visitJumpInsn(IFNULL, l6);
      mv.visitVarInsn(ALOAD, 23);
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true);
      mv.visitJumpInsn(IFLE, l6);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitLdcInsn("Date");
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get",
          "(Ljava/lang/Object;)Ljava/lang/Object;", true);
      mv.visitTypeInsn(CHECKCAST, "java/util/List");
      mv.visitInsn(ICONST_0);
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
      mv.visitTypeInsn(CHECKCAST, "java/lang/String");
      mv.visitInsn(DUP);
      mv.visitVarInsn(ASTORE, 23);
      mv.visitJumpInsn(IFNULL, l6);
      mv.visitVarInsn(ALOAD, 23);
      mv.visitMethodInsn(INVOKESTATIC, CLASS_INTERNAL, "a", "(Ljava/lang/String;)J",
          false);
      mv.visitVarInsn(LSTORE, 10);
      mv.visitLabel(l6);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitLdcInsn("Cache-Control");
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get",
          "(Ljava/lang/Object;)Ljava/lang/Object;", true);
      mv.visitTypeInsn(CHECKCAST, "java/util/List");
      mv.visitInsn(DUP);
      mv.visitVarInsn(ASTORE, 23);
      Label l7 = new Label();
      mv.visitJumpInsn(IFNULL, l7);
      mv.visitVarInsn(ALOAD, 23);
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true);
      mv.visitJumpInsn(IFLE, l7);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitLdcInsn("Cache-Control");
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get",
          "(Ljava/lang/Object;)Ljava/lang/Object;", true);
      mv.visitTypeInsn(CHECKCAST, "java/util/List");
      mv.visitInsn(ICONST_0);
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
      mv.visitTypeInsn(CHECKCAST, "java/lang/String");
      mv.visitInsn(DUP);
      mv.visitVarInsn(ASTORE, 23);
      mv.visitJumpInsn(IFNULL, l7);
      mv.visitInsn(ICONST_1);
      mv.visitVarInsn(ISTORE, 7);
      mv.visitVarInsn(ALOAD, 23);
      mv.visitLdcInsn(",");
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "split",
          "(Ljava/lang/String;)[Ljava/lang/String;", false);
      mv.visitVarInsn(ASTORE, 23);
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ISTORE, 24);
      Label l8 = new Label();
      mv.visitLabel(l8);
      mv.visitVarInsn(ILOAD, 24);
      mv.visitVarInsn(ALOAD, 23);
      mv.visitInsn(ARRAYLENGTH);
      mv.visitJumpInsn(IF_ICMPGE, l7);
      mv.visitVarInsn(ALOAD, 23);
      mv.visitVarInsn(ILOAD, 24);
      mv.visitInsn(AALOAD);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false);
      mv.visitInsn(DUP);
      mv.visitVarInsn(ASTORE, 25);
      mv.visitLdcInsn("no-cache");
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z",
          false);
      Label l9 = new Label();
      mv.visitJumpInsn(IFNE, l9);
      mv.visitVarInsn(ALOAD, 25);
      mv.visitLdcInsn("no-store");
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z",
          false);
      mv.visitJumpInsn(IFNE, l9);
      mv.visitVarInsn(ALOAD, 25);
      mv.visitLdcInsn("max-age=");
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z",
          false);
      Label l10 = new Label();
      mv.visitJumpInsn(IFEQ, l10);
      mv.visitLabel(l0);
      mv.visitVarInsn(ALOAD, 25);
      mv.visitIntInsn(BIPUSH, 8);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;",
          false);
      mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "parseLong", "(Ljava/lang/String;)J",
          false);
      mv.visitVarInsn(LSTORE, 18);
      mv.visitLabel(l1);
      mv.visitJumpInsn(GOTO, l9);
      mv.visitLabel(l2);
      mv.visitVarInsn(ASTORE, 25);
      mv.visitMethodInsn(INVOKESTATIC, CLASS_INTERNAL, "b", "()Ljava/lang/String;", false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 25);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Exception", "getMessage", "()Ljava/lang/String;",
          false);
      mv.visitInsn(POP);
      mv.visitJumpInsn(GOTO, l9);
      mv.visitLabel(l10);
      mv.visitVarInsn(ALOAD, 25);
      mv.visitLdcInsn("stale-while-revalidate=");
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z",
          false);
      Label l11 = new Label();
      mv.visitJumpInsn(IFEQ, l11);
      mv.visitLabel(l3);
      mv.visitVarInsn(ALOAD, 25);
      mv.visitIntInsn(BIPUSH, 23);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;",
          false);
      mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "parseLong", "(Ljava/lang/String;)J",
          false);
      mv.visitVarInsn(LSTORE, 20);
      mv.visitLabel(l4);
      mv.visitJumpInsn(GOTO, l9);
      mv.visitLabel(l5);
      mv.visitVarInsn(ASTORE, 25);
      mv.visitMethodInsn(INVOKESTATIC, CLASS_INTERNAL, "b", "()Ljava/lang/String;",
          false);
      mv.visitInsn(POP);
      mv.visitVarInsn(ALOAD, 25);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Exception", "getMessage", "()Ljava/lang/String;",
          false);
      mv.visitInsn(POP);
      mv.visitJumpInsn(GOTO, l9);
      mv.visitLabel(l11);
      mv.visitVarInsn(ALOAD, 25);
      mv.visitLdcInsn("must-revalidate");
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z",
          false);
      Label l12 = new Label();
      mv.visitJumpInsn(IFNE, l12);
      mv.visitVarInsn(ALOAD, 25);
      mv.visitLdcInsn("proxy-revalidate");
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z",
          false);
      mv.visitJumpInsn(IFEQ, l9);
      mv.visitLabel(l12);
      mv.visitInsn(ICONST_1);
      mv.visitVarInsn(ISTORE, 22);
      mv.visitLabel(l9);
      mv.visitIincInsn(24, 1);
      mv.visitJumpInsn(GOTO, l8);
      mv.visitLabel(l7);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitLdcInsn("Expires");
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get",
          "(Ljava/lang/Object;)Ljava/lang/Object;", true);
      mv.visitTypeInsn(CHECKCAST, "java/util/List");
      mv.visitInsn(DUP);
      mv.visitVarInsn(ASTORE, 23);
      Label l13 = new Label();
      mv.visitJumpInsn(IFNULL, l13);
      mv.visitVarInsn(ALOAD, 23);
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true);
      mv.visitJumpInsn(IFLE, l13);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitLdcInsn("Expires");
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get",
          "(Ljava/lang/Object;)Ljava/lang/Object;", true);
      mv.visitTypeInsn(CHECKCAST, "java/util/List");
      mv.visitInsn(ICONST_0);
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
      mv.visitTypeInsn(CHECKCAST, "java/lang/String");
      mv.visitInsn(DUP);
      mv.visitVarInsn(ASTORE, 23);
      mv.visitJumpInsn(IFNULL, l13);
      mv.visitVarInsn(ALOAD, 23);
      mv.visitMethodInsn(INVOKESTATIC, CLASS_INTERNAL, "a", "(Ljava/lang/String;)J",
          false);
      mv.visitVarInsn(LSTORE, 12);
      mv.visitLabel(l13);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitLdcInsn("Last-Modified");
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get",
          "(Ljava/lang/Object;)Ljava/lang/Object;", true);
      mv.visitTypeInsn(CHECKCAST, "java/util/List");
      mv.visitInsn(DUP);
      mv.visitVarInsn(ASTORE, 23);
      Label l14 = new Label();
      mv.visitJumpInsn(IFNULL, l14);
      mv.visitVarInsn(ALOAD, 23);
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true);
      mv.visitJumpInsn(IFLE, l14);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitLdcInsn("Last-Modified");
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get",
          "(Ljava/lang/Object;)Ljava/lang/Object;", true);
      mv.visitTypeInsn(CHECKCAST, "java/util/List");
      mv.visitInsn(ICONST_0);
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
      mv.visitTypeInsn(CHECKCAST, "java/lang/String");
      mv.visitInsn(DUP);
      mv.visitVarInsn(ASTORE, 23);
      mv.visitJumpInsn(IFNULL, l14);
      mv.visitVarInsn(ALOAD, 23);
      mv.visitMethodInsn(INVOKESTATIC, CLASS_INTERNAL, "a", "(Ljava/lang/String;)J",
          false);
      mv.visitInsn(POP2);
      mv.visitLabel(l14);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitLdcInsn("ETag");
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get",
          "(Ljava/lang/Object;)Ljava/lang/Object;", true);
      mv.visitTypeInsn(CHECKCAST, "java/util/List");
      mv.visitInsn(DUP);
      mv.visitVarInsn(ASTORE, 23);
      Label l15 = new Label();
      mv.visitJumpInsn(IFNULL, l15);
      mv.visitVarInsn(ALOAD, 23);
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true);
      mv.visitJumpInsn(IFLE, l15);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitLdcInsn("ETag");
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get",
          "(Ljava/lang/Object;)Ljava/lang/Object;", true);
      mv.visitTypeInsn(CHECKCAST, "java/util/List");
      mv.visitInsn(ICONST_0);
      mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
      mv.visitInsn(POP);
      mv.visitLabel(l15);
      mv.visitVarInsn(ILOAD, 7);
      Label l16 = new Label();
      mv.visitJumpInsn(IFEQ, l16);
      mv.visitVarInsn(LLOAD, 8);
      mv.visitVarInsn(LLOAD, 18);
      mv.visitLdcInsn(new Long(1000L));
      mv.visitInsn(LMUL);
      mv.visitInsn(LADD);
      mv.visitVarInsn(LSTORE, 14);
      mv.visitVarInsn(ILOAD, 22);
      Label l17 = new Label();
      mv.visitJumpInsn(IFEQ, l17);
      mv.visitVarInsn(LLOAD, 14);
      Label l18 = new Label();
      mv.visitJumpInsn(GOTO, l18);
      mv.visitLabel(l17);
      mv.visitVarInsn(LLOAD, 14);
      mv.visitVarInsn(LLOAD, 20);
      mv.visitLdcInsn(new Long(1000L));
      mv.visitInsn(LMUL);
      mv.visitInsn(LADD);
      mv.visitLabel(l18);
      mv.visitVarInsn(LSTORE, 16);
      Label l19 = new Label();
      mv.visitJumpInsn(GOTO, l19);
      mv.visitLabel(l16);
      mv.visitVarInsn(LLOAD, 10);
      mv.visitInsn(LCONST_0);
      mv.visitInsn(LCMP);
      mv.visitJumpInsn(IFLE, l19);
      mv.visitVarInsn(LLOAD, 12);
      mv.visitVarInsn(LLOAD, 10);
      mv.visitInsn(LCMP);
      mv.visitJumpInsn(IFLT, l19);
      mv.visitVarInsn(LLOAD, 8);
      mv.visitVarInsn(LLOAD, 12);
      mv.visitVarInsn(LLOAD, 10);
      mv.visitInsn(LSUB);
      mv.visitInsn(LADD);
      mv.visitInsn(DUP2);
      mv.visitVarInsn(LSTORE, 14);
      mv.visitVarInsn(LSTORE, 16);
      mv.visitLabel(l19);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitFieldInsn(PUTFIELD, CLASS_INTERNAL, "c", "Ljava/lang/String;");
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 2);
      mv.visitFieldInsn(PUTFIELD, CLASS_INTERNAL, "d", "Ljava/lang/String;");
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ILOAD, 4);
      mv.visitFieldInsn(PUTFIELD, CLASS_INTERNAL, "b", "I");
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(LLOAD, 8);
      mv.visitVarInsn(LLOAD, 5);
      mv.visitLdcInsn(new Long(1000L));
      mv.visitInsn(LMUL);
      mv.visitInsn(LADD);
      mv.visitFieldInsn(PUTFIELD, CLASS_INTERNAL, "g", "J");
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(LLOAD, 14);
      mv.visitFieldInsn(PUTFIELD, CLASS_INTERNAL, "h", "J");
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitFieldInsn(GETFIELD, CLASS_INTERNAL, "g", "J");
      mv.visitVarInsn(LLOAD, 16);
      mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "min", "(JJ)J", false);
      mv.visitFieldInsn(PUTFIELD, CLASS_INTERNAL, "g", "J");
      mv.visitVarInsn(ALOAD, 0);
      mv.visitInsn(ARETURN);
      mv.visitMaxs(7, 26);
      mv.visitEnd();
    }

    cw.visitEnd();

    return cw.toByteArray();
  }
}

