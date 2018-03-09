// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

// Generated with
// tools/asmifier.py build/classes/test/com/android/tools/r8/cf/AlwaysNullGetItemTest.class
// and edited to replace calls to get{Object,Typed}Array() with ACONST_NULL (without CHECKCAST).
public class AlwaysNullGetItemDump implements Opcodes {
  public static byte[] dump() throws Exception {

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;
    AnnotationVisitor av0;

    cw.visit(
        V1_8,
        ACC_PUBLIC + ACC_SUPER,
        "com/android/tools/r8/cf/AlwaysNullGetItemTest",
        null,
        "java/lang/Object",
        null);

    cw.visitInnerClass(
        "com/android/tools/r8/cf/AlwaysNullGetItemTest$A",
        "com/android/tools/r8/cf/AlwaysNullGetItemTest",
        "A",
        ACC_PRIVATE + ACC_STATIC);

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
      Label l0 = new Label();
      Label l1 = new Label();
      mv.visitTryCatchBlock(l0, l1, l1, "java/lang/NullPointerException");
      mv.visitLabel(l0);
      mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      mv.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/cf/AlwaysNullGetItemTest",
          "foo",
          "()Ljava/lang/Object;",
          false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
      mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      mv.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/cf/AlwaysNullGetItemTest",
          "bar",
          "()Ljava/lang/Object;",
          false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
      mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      mv.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/cf/AlwaysNullGetItemTest",
          "hello",
          "()Lcom/android/tools/r8/cf/AlwaysNullGetItemTest$A;",
          false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "com/android/tools/r8/cf/AlwaysNullGetItemTest$A",
          "hello",
          "()Lcom/android/tools/r8/cf/AlwaysNullGetItemTest$A;",
          false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
      mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      mv.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/cf/AlwaysNullGetItemTest",
          "goodbye",
          "()Lcom/android/tools/r8/cf/AlwaysNullGetItemTest$A;",
          false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "com/android/tools/r8/cf/AlwaysNullGetItemTest$A",
          "hello",
          "()Lcom/android/tools/r8/cf/AlwaysNullGetItemTest$A;",
          false);
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
      mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
      mv.visitInsn(DUP);
      mv.visitLdcInsn("Expected NullPointerException");
      mv.visitMethodInsn(
          INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false);
      mv.visitInsn(ATHROW);
      mv.visitLabel(l1);
      mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/NullPointerException"});
      mv.visitVarInsn(ASTORE, 1);
      mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      mv.visitLdcInsn("NullPointerException");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(3, 2);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PRIVATE + ACC_STATIC, "foo", "()Ljava/lang/Object;", null, null);
      mv.visitCode();
      mv.visitInsn(ACONST_NULL);
      // mv.visitTypeInsn(CHECKCAST, "[Ljava/lang/Object;");
      mv.visitInsn(ICONST_0);
      mv.visitInsn(AALOAD);
      mv.visitInsn(ARETURN);
      mv.visitMaxs(2, 0);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PRIVATE + ACC_STATIC, "bar", "()Ljava/lang/Object;", null, null);
      mv.visitCode();
      // mv.visitMethodInsn(
      //     INVOKESTATIC,
      //     "com/android/tools/r8/cf/AlwaysNullGetItemTest",
      //     "getObjectArray",
      //     "()[Ljava/lang/Object;",
      //     false);
      mv.visitInsn(ACONST_NULL);
      mv.visitInsn(ICONST_0);
      mv.visitInsn(AALOAD);
      mv.visitInsn(ARETURN);
      mv.visitMaxs(2, 0);
      mv.visitEnd();
    }
    {
      mv =
          cw.visitMethod(
              ACC_PRIVATE + ACC_STATIC,
              "hello",
              "()Lcom/android/tools/r8/cf/AlwaysNullGetItemTest$A;",
              null,
              null);
      mv.visitCode();
      // mv.visitMethodInsn(
      //     INVOKESTATIC,
      //     "com/android/tools/r8/cf/AlwaysNullGetItemTest",
      //     "getTypedArray",
      //     "()[Lcom/android/tools/r8/cf/AlwaysNullGetItemTest$A;",
      //     false);
      mv.visitInsn(ACONST_NULL);
      mv.visitInsn(ICONST_0);
      mv.visitInsn(AALOAD);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "com/android/tools/r8/cf/AlwaysNullGetItemTest$A",
          "hello",
          "()Lcom/android/tools/r8/cf/AlwaysNullGetItemTest$A;",
          false);
      mv.visitInsn(ARETURN);
      mv.visitMaxs(2, 0);
      mv.visitEnd();
    }
    {
      mv =
          cw.visitMethod(
              ACC_PRIVATE + ACC_STATIC,
              "goodbye",
              "()Lcom/android/tools/r8/cf/AlwaysNullGetItemTest$A;",
              null,
              null);
      mv.visitCode();
      // mv.visitMethodInsn(
      //     INVOKESTATIC,
      //     "com/android/tools/r8/cf/AlwaysNullGetItemTest",
      //     "getTypedArray",
      //     "()[Lcom/android/tools/r8/cf/AlwaysNullGetItemTest$A;",
      //     false);
      mv.visitInsn(ACONST_NULL);
      mv.visitInsn(ICONST_0);
      mv.visitInsn(AALOAD);
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          "com/android/tools/r8/cf/AlwaysNullGetItemTest$A",
          "goodbye",
          "()Lcom/android/tools/r8/cf/AlwaysNullGetItemTest$A;",
          false);
      mv.visitInsn(ARETURN);
      mv.visitMaxs(2, 0);
      mv.visitEnd();
    }
    {
      mv =
          cw.visitMethod(
              ACC_PRIVATE + ACC_STATIC, "getObjectArray", "()[Ljava/lang/Object;", null, null);
      mv.visitCode();
      mv.visitInsn(ACONST_NULL);
      mv.visitInsn(ARETURN);
      mv.visitMaxs(1, 0);
      mv.visitEnd();
    }
    {
      mv =
          cw.visitMethod(
              ACC_PRIVATE + ACC_STATIC,
              "getTypedArray",
              "()[Lcom/android/tools/r8/cf/AlwaysNullGetItemTest$A;",
              null,
              null);
      mv.visitCode();
      mv.visitInsn(ACONST_NULL);
      mv.visitInsn(ARETURN);
      mv.visitMaxs(1, 0);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }
}
