// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda.b159688129;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MainKtDump implements Opcodes {

  public static byte[] dump(int id, int numberOfLambdas) {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_8,
        ACC_PUBLIC | ACC_FINAL | ACC_SUPER,
        "com/android/tools/r8/kotlin/lambda/b159688129/MainKt" + id,
        null,
        "java/lang/Object",
        null);

    {
      annotationVisitor0 = classWriter.visitAnnotation("Lkotlin/Metadata;", true);
      annotationVisitor0.visit("mv", new int[] {1, 1, 16});
      annotationVisitor0.visit("bv", new int[] {1, 0, 3});
      annotationVisitor0.visit("k", new Integer(2));
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d1");
        annotationVisitor1.visit(
            null,
            "\u0000\u0016\n"
                + "\u0000\n"
                + "\u0002\u0010\u0002\n"
                + "\u0002\u0008\u0002\n"
                + "\u0002\u0018\u0002\n"
                + "\u0002\u0010\u0008\n"
                + "\u0002\u0008\u0002\u001a\u0006\u0010\u0000\u001a\u00020\u0001\u001a$\u0010\u0002\u001a\u00020\u00012\u0012\u0010\u0003\u001a\u000e\u0012\u0004\u0012\u00020\u0005\u0012\u0004\u0012\u00020\u00010\u00042\u0006\u0010\u0006\u001a\u00020\u0005H\u0007\u00a8\u0006\u0007");
        annotationVisitor1.visitEnd();
      }
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d2");
        annotationVisitor1.visit(null, "main");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visit(null, "run");
        annotationVisitor1.visit(null, "param");
        annotationVisitor1.visit(null, "Lkotlin/Function1;");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visit(null, "arg");
        annotationVisitor1.visit(null, "r8.main");
        annotationVisitor1.visitEnd();
      }
      annotationVisitor0.visitEnd();
    }
    for (int lambdaId = 0; lambdaId < numberOfLambdas; lambdaId++) {
      if (lambdaId > 0) {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "main" + lambdaId, "()V", null, null);
      } else {
        methodVisitor =
            classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "main", "()V", null, null);
      }
      methodVisitor.visitCode();
      methodVisitor.visitFieldInsn(
          GETSTATIC,
          "com/android/tools/r8/kotlin/lambda/b159688129/MainKt" + id + "$main$" + lambdaId,
          "INSTANCE",
          "Lcom/android/tools/r8/kotlin/lambda/b159688129/MainKt" + id + "$main$" + lambdaId + ";");
      methodVisitor.visitTypeInsn(CHECKCAST, "kotlin/jvm/functions/Function1");
      methodVisitor.visitInsn(ICONST_3);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/kotlin/lambda/b159688129/MainKt" + id,
          "run",
          "(Lkotlin/jvm/functions/Function1;I)V",
          false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 0);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC,
              "main",
              "([Ljava/lang/String;)V",
              null,
              null);
      methodVisitor.visitCode();
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/kotlin/lambda/b159688129/MainKt" + id,
          "main",
          "()V",
          false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(0, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_FINAL | ACC_STATIC,
              "run",
              "(Lkotlin/jvm/functions/Function1;I)V",
              "(Lkotlin/jvm/functions/Function1<-Ljava/lang/Integer;Lkotlin/Unit;>;I)V",
              null);
      {
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitAnnotableParameterCount(2, false);
      {
        annotationVisitor0 =
            methodVisitor.visitParameterAnnotation(0, "Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitLdcInsn("param");
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "kotlin/jvm/internal/Intrinsics",
          "checkParameterIsNotNull",
          "(Ljava/lang/Object;Ljava/lang/String;)V",
          false);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE,
          "kotlin/jvm/functions/Function1",
          "invoke",
          "(Ljava/lang/Object;)Ljava/lang/Object;",
          true);
      methodVisitor.visitInsn(POP);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
