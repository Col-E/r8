// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda.b159688129;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class TestKtDump implements Opcodes {

  public static byte[] dump(int id) {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_8,
        ACC_PUBLIC | ACC_FINAL | ACC_SUPER,
        "com/android/tools/r8/kotlin/lambda/b159688129/TestKt" + id,
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
            "\u0000\u0008\n"
                + "\u0000\n"
                + "\u0002\u0010\u0002\n"
                + "\u0000\u001a\u0006\u0010\u0000\u001a\u00020\u0001\u00a8\u0006\u0002");
        annotationVisitor1.visitEnd();
      }
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d2");
        annotationVisitor1.visit(null, "test");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visit(null, "r8.main");
        annotationVisitor1.visitEnd();
      }
      annotationVisitor0.visitEnd();
    }
    classWriter.visitInnerClass(
        "com/android/tools/r8/kotlin/lambda/b159688129/TestKt" + id + "$test$1",
        null,
        null,
        ACC_FINAL | ACC_STATIC);

    {
      methodVisitor =
          classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "test", "()V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitFieldInsn(
          GETSTATIC,
          "com/android/tools/r8/kotlin/lambda/b159688129/TestKt" + id + "$test$1",
          "INSTANCE",
          "Lcom/android/tools/r8/kotlin/lambda/b159688129/TestKt" + id + "$test$1;");
      methodVisitor.visitTypeInsn(CHECKCAST, "kotlin/jvm/functions/Function0");
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/kotlin/lambda/b159688129/MainKt" + id,
          "run",
          "(Lkotlin/jvm/functions/Function0;)V",
          false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 0);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
