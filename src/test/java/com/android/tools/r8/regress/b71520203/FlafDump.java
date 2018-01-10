// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b71520203;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class FlafDump implements Opcodes {

  public static byte[] dump () throws Exception {
    ClassWriter cw = new ClassWriter(0);
    MethodVisitor mv;

    cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, "Flaf", null, "java/lang/Object", null);

    cw.visitInnerClass("Flaf$A", "Flaf", "A", ACC_PUBLIC + ACC_STATIC);

    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      mv.visitCode();
      mv.visitTypeInsn(NEW, "Flaf$A");
      mv.visitInsn(DUP);
      mv.visitMethodInsn(INVOKESPECIAL, "Flaf$A", "<init>", "()V", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "Flaf$A", "foo", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 1);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }
}
