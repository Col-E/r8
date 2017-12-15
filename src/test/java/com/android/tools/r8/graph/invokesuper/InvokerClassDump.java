// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.invokesuper;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * This is a modified version of {@link InvokerClass} with invoke special instructions corresponding
 * to the methods' names.
 */
public class InvokerClassDump implements Opcodes {

  public static byte[] dump() throws Exception {

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;
    AnnotationVisitor av0;

    cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, "com/android/tools/r8/graph/invokesuper/InvokerClass",
        null, "com/android/tools/r8/graph/invokesuper/SubLevel2", null);

    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "com/android/tools/r8/graph/invokesuper/SubLevel2",
          "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "invokeSuperMethodOnSubLevel2", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "com/android/tools/r8/graph/invokesuper/SubLevel2",
          "superMethod", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "invokeSuperMethodOnSubLevel1", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "com/android/tools/r8/graph/invokesuper/SubLevel1",
          "superMethod", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "invokeSuperMethodOnSuper", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "com/android/tools/r8/graph/invokesuper/Super",
          "superMethod", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "invokeSubLevel1MethodOnSubLevel2", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "com/android/tools/r8/graph/invokesuper/SubLevel2",
          "subLevel1Method", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "invokeSubLevel1MethodOnSubLevel1", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "com/android/tools/r8/graph/invokesuper/SubLevel1",
          "subLevel1Method", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "invokeSubLevel1MethodOnSuper", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "com/android/tools/r8/graph/invokesuper/Super",
          "subLevel1Method", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC, "invokeSubLevel2MethodOnSubLevel2", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "com/android/tools/r8/graph/invokesuper/SubLevel2",
          "subLevel2Method", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    // The below fails to verify on the JavaVM, so we cannot test it there.
    // {
    //   mv = cw.visitMethod(ACC_PUBLIC, "invokeSubLevel2MethodOnSubClassOfInvokerClass", "()V",
    //       null,
    //       null);
    //   mv.visitCode();
    //   mv.visitVarInsn(ALOAD, 0);
    //   mv.visitMethodInsn(INVOKESPECIAL,
    //       "com/android/tools/r8/graph/invokesuper/SubclassOfInvokerClass",
    //       "subLevel2Method", "()V", false);
    //   mv.visitInsn(RETURN);
    //   mv.visitMaxs(1, 1);
    //   mv.visitEnd();
    // }
    cw.visitEnd();

    return cw.toByteArray();
  }
}

