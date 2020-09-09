// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda.b159688129;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class MainKt$main$1Dump implements Opcodes {

  public static byte[] dump(int mainId, int lambdaId) {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_8,
        ACC_FINAL | ACC_SUPER,
        "com/android/tools/r8/kotlin/lambda/b159688129/MainKt" + mainId + "$main$" + lambdaId,
        "Lkotlin/jvm/internal/Lambda;Lkotlin/jvm/functions/Function1<Ljava/lang/Integer;Lkotlin/Unit;>;",
        "kotlin/jvm/internal/Lambda",
        new String[] {"kotlin/jvm/functions/Function1"});

    classWriter.visitOuterClass(
        "com/android/tools/r8/kotlin/lambda/b159688129/MainKt" + mainId,
        lambdaId > 0 ? "main" + lambdaId : "main",
        "()V");

    {
      annotationVisitor0 = classWriter.visitAnnotation("Lkotlin/Metadata;", true);
      annotationVisitor0.visit("mv", new int[] {1, 1, 16});
      annotationVisitor0.visit("bv", new int[] {1, 0, 3});
      annotationVisitor0.visit("k", new Integer(3));
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d1");
        annotationVisitor1.visit(
            null,
            "\u0000\u000e\n"
                + "\u0000\n"
                + "\u0002\u0010\u0002\n"
                + "\u0000\n"
                + "\u0002\u0010\u0008\n"
                + "\u0000\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u0003H\n"
                + "\u00a2\u0006\u0002\u0008\u0004");
        annotationVisitor1.visitEnd();
      }
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d2");
        annotationVisitor1.visit(null, "<anonymous>");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visit(null, "arg");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visit(null, "invoke");
        annotationVisitor1.visitEnd();
      }
      annotationVisitor0.visitEnd();
    }
    classWriter.visitInnerClass(
        "com/android/tools/r8/kotlin/lambda/b159688129/MainKt" + mainId + "$main$" + lambdaId,
        null,
        null,
        ACC_FINAL | ACC_STATIC);
    {
      fieldVisitor =
          classWriter.visitField(
              ACC_PUBLIC | ACC_FINAL | ACC_STATIC,
              "INSTANCE",
              "Lcom/android/tools/r8/kotlin/lambda/b159688129/MainKt"
                  + mainId
                  + "$main$"
                  + lambdaId
                  + ";",
              null,
              null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_BRIDGE | ACC_SYNTHETIC,
              "invoke",
              "(Ljava/lang/Object;)Ljava/lang/Object;",
              null,
              null);
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/Number");
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Number", "intValue", "()I", false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL,
          "com/android/tools/r8/kotlin/lambda/b159688129/MainKt" + mainId + "$main$" + lambdaId,
          "invoke",
          "(I)V",
          false);
      methodVisitor.visitFieldInsn(GETSTATIC, "kotlin/Unit", "INSTANCE", "Lkotlin/Unit;");
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL, "invoke", "(I)V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitInsn(ICONST_0);
      methodVisitor.visitVarInsn(ISTORE, 2);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 3);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "kotlin/jvm/internal/Lambda", "<init>", "(I)V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitTypeInsn(
          NEW,
          "com/android/tools/r8/kotlin/lambda/b159688129/MainKt" + mainId + "$main$" + lambdaId);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "com/android/tools/r8/kotlin/lambda/b159688129/MainKt" + mainId + "$main$" + lambdaId,
          "<init>",
          "()V",
          false);
      methodVisitor.visitFieldInsn(
          PUTSTATIC,
          "com/android/tools/r8/kotlin/lambda/b159688129/MainKt" + mainId + "$main$" + lambdaId,
          "INSTANCE",
          "Lcom/android/tools/r8/kotlin/lambda/b159688129/MainKt"
              + mainId
              + "$main$"
              + lambdaId
              + ";");
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 0);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
