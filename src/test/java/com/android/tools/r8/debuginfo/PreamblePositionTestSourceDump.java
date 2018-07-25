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

/*
Generated from the source code below, line numbers removed, except for the false branch,
which is set to FALSE_BRANCH_LINE_NUMBER.

    package com.android.tools.r8.debuginfo;

    public class PreamblePositionTestSource {
      public static void main(String[] args) {
        System.err.println("<first-line>");
        if (args.length == 0) {
          throw new RuntimeException("<true-branch-exception>");
        } else {
          throw new RuntimeException("<false-branch-exception>");
        }
      }
    }
*/

public class PreamblePositionTestSourceDump implements Opcodes {

  static final int FALSE_BRANCH_LINE_NUMBER = 123;

  public static byte[] dump() throws Exception {

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;
    AnnotationVisitor av0;

    cw.visit(
        V1_8,
        ACC_PUBLIC + ACC_SUPER,
        "com/android/tools/r8/debuginfo/PreamblePositionTestSource",
        null,
        "java/lang/Object",
        null);

    cw.visitSource("PreamblePositionTestSource.java", null);

    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      mv.visitCode();
      Label l0 = new Label();
      mv.visitLabel(l0);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      Label l1 = new Label();
      mv.visitLabel(l1);
      mv.visitLocalVariable(
          "this", "Lcom/android/tools/r8/debuginfo/PreamblePositionTestSource;", null, l0, l1, 0);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      mv.visitCode();
      Label l0 = new Label();
      mv.visitLabel(l0);
      mv.visitFieldInsn(GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;");
      mv.visitLdcInsn("<first-line>");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label l1 = new Label();
      mv.visitLabel(l1);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitInsn(ARRAYLENGTH);
      Label l2 = new Label();
      mv.visitJumpInsn(IFNE, l2);
      Label l3 = new Label();
      mv.visitLabel(l3);
      mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
      mv.visitInsn(DUP);
      mv.visitLdcInsn("<true-branch-exception>");
      mv.visitMethodInsn(
          INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false);
      mv.visitInsn(ATHROW);
      mv.visitLabel(l2);
      mv.visitLineNumber(FALSE_BRANCH_LINE_NUMBER, l2);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
      mv.visitInsn(DUP);
      mv.visitLdcInsn("<false-branch-exception>");
      mv.visitMethodInsn(
          INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false);
      mv.visitInsn(ATHROW);
      Label l4 = new Label();
      mv.visitLabel(l4);
      mv.visitLocalVariable("args", "[Ljava/lang/String;", null, l0, l4, 0);
      mv.visitMaxs(3, 1);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }
}
