// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import java.util.*;
import org.objectweb.asm.*;

public class UninitializedInFrameDump implements Opcodes {

  public static byte[] dump() throws Exception {

    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    FieldVisitor fv;
    MethodVisitor mv;
    AnnotationVisitor av0;

    cw.visit(
        V1_8,
        ACC_PUBLIC + ACC_SUPER,
        "com/android/tools/r8/cf/UninitializedInFrameTest",
        null,
        "java/lang/Object",
        null);

    // The constructor UninitializedInFrameTest(int i) has been modified
    // to add a jump back to the entry block.
    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(I)V", null, null);
      mv.visitCode();
      Label l = new Label(); // Added
      mv.visitLabel(l); // Added
      mv.visitVarInsn(ALOAD, 0);
      mv.visitVarInsn(ILOAD, 1);
      mv.visitInsn(ICONST_1);
      mv.visitInsn(ISUB);
      mv.visitInsn(DUP); // Added
      mv.visitIntInsn(BIPUSH, 42);
      Label l0 = new Label();
      mv.visitJumpInsn(IF_ICMPLT, l0);
      // mv.visitInsn(ICONST_1);
      mv.visitVarInsn(ISTORE, 1); // Added
      mv.visitInsn(POP); // Added
      mv.visitJumpInsn(GOTO, l); // Added
      Label l1 = new Label();
      mv.visitJumpInsn(GOTO, l1);
      mv.visitLabel(l0);
      mv.visitInsn(POP); // Added
      mv.visitInsn(ICONST_0);
      mv.visitLabel(l1);
      mv.visitMethodInsn(
          INVOKESPECIAL,
          "com/android/tools/r8/cf/UninitializedInFrameTest",
          "<init>",
          "(Z)V",
          false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(-1, -1);
      // mv.visitMaxs(3, 2);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PRIVATE, "<init>", "(Z)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(-1, -1);
      // mv.visitMaxs(1, 2);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitInsn(ARRAYLENGTH);
      Label l0 = new Label();
      mv.visitJumpInsn(IFEQ, l0);
      mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
      mv.visitInsn(DUP);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitInsn(ARRAYLENGTH);
      mv.visitIntInsn(BIPUSH, 42);
      Label l1 = new Label();
      mv.visitJumpInsn(IF_ICMPNE, l1);
      // mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
      // mv.visitInsn(DUP);
      mv.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
      mv.visitInsn(DUP);
      mv.visitMethodInsn(
          INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "()V", false);
      mv.visitMethodInsn(
          INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/Throwable;)V", false);
      mv.visitVarInsn(ASTORE, 1);
      // At this point, stack is empty.
      Label l2 = new Label();
      mv.visitJumpInsn(GOTO, l2);
      mv.visitLabel(l1);
      // At this point, stack contains two copies of uninitialized RuntimeException.
      // mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      // mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
      // mv.visitInsn(DUP);
      mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
      mv.visitInsn(DUP);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
      mv.visitLdcInsn("You supplied ");
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitInsn(ARRAYLENGTH);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(I)Ljava/lang/StringBuilder;",
          false);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitInsn(ARRAYLENGTH);
      mv.visitInsn(ICONST_1);
      Label l3 = new Label();
      mv.visitJumpInsn(IF_ICMPNE, l3);
      mv.visitLdcInsn(" arg");
      Label l4 = new Label();
      mv.visitJumpInsn(GOTO, l4);
      mv.visitLabel(l3);
      // At this point, stack contains two copies of uninitialized RuntimeException.
      // Note that asmifier seems to produce incorrect labels for the uninitialized type.
      // mv.visitFrame(Opcodes.F_FULL, 1, new Object[] {"[Ljava/lang/String;"}, 3, new Object[] {l1,
      // l1, "java/lang/StringBuilder"});
      mv.visitLdcInsn(" args");
      mv.visitLabel(l4);
      // At this point, stack contains two copies of uninitialized RuntimeException.
      // mv.visitFrame(Opcodes.F_FULL, 1, new Object[] {"[Ljava/lang/String;"}, 4, new Object[] {l1,
      // l1, "java/lang/StringBuilder", "java/lang/String"});
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/StringBuilder",
          "append",
          "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
          false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
      mv.visitMethodInsn(
          INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false);
      mv.visitVarInsn(ASTORE, 1);
      mv.visitLabel(l2);
      // At this point, stack is empty, and local 1 contains an initialized RuntimeException.
      // mv.visitFrame(Opcodes.F_APPEND,1, new Object[] {"java/lang/RuntimeException"}, 0, null);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitInsn(ARRAYLENGTH);
      mv.visitInsn(ICONST_2);
      mv.visitInsn(IREM);
      Label l5 = new Label();
      mv.visitJumpInsn(IFNE, l5);
      mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      mv.visitVarInsn(ALOAD, 1);
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
      mv.visitLabel(l5);
      // mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitInsn(ATHROW);
      mv.visitLabel(l0);
      // mv.visitFrame(Opcodes.F_CHOP,1, null, 0, null);
      mv.visitInsn(RETURN);
      mv.visitMaxs(-1, -1);
      // mv.visitMaxs(5, 2);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }
}
