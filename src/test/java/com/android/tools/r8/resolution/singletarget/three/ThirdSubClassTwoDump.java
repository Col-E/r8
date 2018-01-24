// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.singletarget.three;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * ASM dump of {@link ThirdSubClassTwo} with abstract flag removed and
 * {@link ThirdSubClassTwo#instanceMethod()} made static.
 */
public class ThirdSubClassTwoDump implements Opcodes {

  public static byte[] dump() throws Exception {

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;
    AnnotationVisitor av0;

    cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER,
        "com/android/tools/r8/resolution/singletarget/three/ThirdSubClassTwo", null,
        "com/android/tools/r8/resolution/singletarget/three/ThirdAbstractTopClass", null);

    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL,
          "com/android/tools/r8/resolution/singletarget/three/ThirdAbstractTopClass", "<init>",
          "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }

    {
      mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "instanceMethod", "()V", null, null);
      mv.visitCode();
      mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      mv.visitLdcInsn(
          Type.getType("Lcom/android/tools/r8/resolution/singletarget/three/ThirdSubClassTwo;"));
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getCanonicalName",
          "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V",
          false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 1);
      mv.visitEnd();
    }

    {
      mv = cw.visitMethod(ACC_PRIVATE, "otherInstanceMethod", "()V", null, null);
      mv.visitCode();
      mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      mv.visitLdcInsn(
          Type.getType("Lcom/android/tools/r8/resolution/singletarget/three/ThirdSubClassTwo;"));
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getCanonicalName",
          "()Ljava/lang/String;", false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V",
          false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 1);
      mv.visitEnd();
    }

    cw.visitEnd();

    return cw.toByteArray();
  }
}

