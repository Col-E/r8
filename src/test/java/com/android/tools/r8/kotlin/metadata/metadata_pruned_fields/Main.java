// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata.metadata_pruned_fields;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class Main implements Opcodes {

  // The dump is generated from the code below, which cannot compile because of a missing
  // reference to MethodsKt.
  //
  // public static void main(String[] args) {
  //   final kotlin.Metadata annotation = MethodsKt.class.getAnnotation(kotlin.Metadata.class);
  //   System.out.println(annotation.pn());
  //   MethodsKt.staticMethod();
  // }

  public static byte[] dump() {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(
        V1_8,
        ACC_PUBLIC | ACC_SUPER,
        "com/android/tools/r8/kotlin/metadata/metadata_pruned_fields/Main",
        null,
        "java/lang/Object",
        new String[] {});

    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitLdcInsn(
          Type.getType("Lcom/android/tools/r8/kotlin/metadata/metadata_pruned_fields/MethodsKt;"));
      methodVisitor.visitLdcInsn(Type.getType("Lkotlin/Metadata;"));
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "java/lang/Class",
          "getAnnotation",
          "(Ljava/lang/Class;)Ljava/lang/annotation/Annotation;",
          false);
      methodVisitor.visitTypeInsn(CHECKCAST, "kotlin/Metadata");
      methodVisitor.visitVarInsn(ASTORE, 1);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE, "kotlin/Metadata", "pn", "()Ljava/lang/String;", true);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/kotlin/metadata/metadata_pruned_fields/MethodsKt",
          "staticMethod",
          "()V",
          false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
