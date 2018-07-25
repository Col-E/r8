// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class IincDebugTestDump implements Opcodes {

  public static final String CLASS_NAME = "IincDebugTest";
  public static final String DESCRIPTOR = "L" + CLASS_NAME + ";";

  public static byte[] dump(int iRegister, int jRegister, boolean useInc) {

    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    MethodVisitor mv;

    cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, CLASS_NAME, null, "java/lang/Object", null);

    {
      mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      mv.visitCode();
      Label methodStart = new Label();
      mv.visitLabel(methodStart);
      mv.visitLineNumber(12, methodStart);
      mv.visitInsn(ICONST_1);
      mv.visitVarInsn(ISTORE, iRegister);
      Label iStart = new Label();
      mv.visitLabel(iStart);
      mv.visitLineNumber(13, iStart);
      if (useInc) {
        assert iRegister == jRegister;
        mv.visitIincInsn(iRegister, 1);
      } else {
        mv.visitVarInsn(ILOAD, iRegister);
        mv.visitInsn(ICONST_1);
        mv.visitInsn(IADD);
        mv.visitVarInsn(ISTORE, jRegister);
      }
      Label iEnd = new Label();
      mv.visitLabel(iEnd);
      mv.visitLineNumber(15, iEnd);
      mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      mv.visitVarInsn(ILOAD, jRegister);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
      Label l3 = new Label();
      mv.visitLabel(l3);
      mv.visitLineNumber(16, l3);
      mv.visitInsn(RETURN);
      Label jEnd = new Label();
      mv.visitLabel(jEnd);
      mv.visitLocalVariable("i", "I", null, iStart, iEnd, iRegister);
      mv.visitLocalVariable("args", "[Ljava/lang/String;", null, methodStart, jEnd, 0);
      mv.visitLocalVariable("j", "I", null, iEnd, jEnd, jRegister);
      mv.visitMaxs(-1, -1);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }
}
