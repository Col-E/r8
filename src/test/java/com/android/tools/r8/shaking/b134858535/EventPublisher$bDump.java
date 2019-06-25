// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.b134858535;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class EventPublisher$bDump implements Opcodes {

  public static byte[] dump() {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_6,
        ACC_FINAL | ACC_SUPER,
        "com/android/tools/r8/shaking/b134858535/EventPublisher$b",
        null,
        "java/lang/Object",
        new String[] {"com/android/tools/r8/shaking/b134858535/Interface"});

    classWriter.visitSource("SourceFile", null);

    {
      annotationVisitor0 = classWriter.visitAnnotation("Lkotlin/Metadata;", true);
      annotationVisitor0.visit("mv", new int[] {1, 1, 15});
      annotationVisitor0.visit("bv", new int[] {1, 0, 3});
      annotationVisitor0.visit("k", new Integer(3));
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d1");
        annotationVisitor1.visit(
            null,
            "\u0000 \n"
                + "\u0000\n"
                + "\u0002\u0018\u0002\n"
                + "\u0002\u0018\u0002\n"
                + "\u0002\u0018\u0002\n"
                + "\u0000\n"
                + "\u0002\u0018\u0002\n"
                + "\u0000\n"
                + "\u0002\u0010 \n"
                + "\u0002\u0018\u0002\n"
                + "\u0000\u0010\u0000\u001a\u0096\u0001\u0012D\u0012B\u0012\u000c\u0012\n"
                + " \u0004*\u0004\u0018\u00010\u00030\u0003\u0012\u000c\u0012\n"
                + " \u0004*\u0004\u0018\u00010\u00050\u0005 \u0004* \u0012\u000c\u0012\n"
                + " \u0004*\u0004\u0018\u00010\u00030\u0003\u0012\u000c\u0012\n"
                + " \u0004*\u0004\u0018\u00010\u00050\u0005\u0018\u00010\u00020\u0002"
                + " \u0004*J\u0012D\u0012B\u0012\u000c\u0012\n"
                + " \u0004*\u0004\u0018\u00010\u00030\u0003\u0012\u000c\u0012\n"
                + " \u0004*\u0004\u0018\u00010\u00050\u0005 \u0004* \u0012\u000c\u0012\n"
                + " \u0004*\u0004\u0018\u00010\u00030\u0003\u0012\u000c\u0012\n"
                + " \u0004*\u0004\u0018\u00010\u00050\u0005\u0018\u00010\u00020\u0002\u0018\u00010\u00010\u00012"
                + " \u0010\u0006\u001a\u001c\u0012\n"
                + "\u0012\u0008\u0012\u0004\u0012\u00020\u00030\u0007\u0012\u000c\u0012\n"
                + " \u0004*\u0004\u0018\u00010\u00080\u00080\u0002H\n"
                + "\u00a2\u0006\u0002\u0008\u0009");
        annotationVisitor1.visitEnd();
      }
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d2");
        annotationVisitor1.visit(null, "<anonymous>");
        annotationVisitor1.visit(null, "Lio/reactivex/Flowable;");
        annotationVisitor1.visit(null, "Lkotlin/Pair;");
        annotationVisitor1.visit(null, "Lcom/permutive/android/event/db/model/EventEntity;");
        annotationVisitor1.visit(null, "kotlin.jvm.PlatformType");
        annotationVisitor1.visit(
            null, "Lcom/permutive/android/event/api/model/TrackBatchEventResponse;");
        annotationVisitor1.visit(null, "<name for destructuring parameter 0>");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visit(null, "Lcom/permutive/android/config/api/model/SdkConfiguration;");
        annotationVisitor1.visit(null, "apply");
        annotationVisitor1.visitEnd();
      }
      annotationVisitor0.visitEnd();
    }
    classWriter.visitInnerClass(
        "com/permutive/android/event/EventPublisher$b", null, null, ACC_FINAL | ACC_STATIC);

    {
      fieldVisitor =
          classWriter.visitField(
              ACC_FINAL | ACC_SYNTHETIC,
              "a",
              "Lcom/permutive/android/event/EventPublisher;",
              null,
              null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(ACC_FINAL | ACC_SYNTHETIC, "b", "Ljava/util/Set;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC,
              "apply",
              "(Ljava/lang/Object;)Ljava/lang/Object;",
              null,
              null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(28, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitTypeInsn(CHECKCAST, "kotlin/Pair");
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "com/android/tools/r8/shaking/b134858535/EventPublisher$b",
          "a",
          "(Ljava/lang/Object;)Ljava/lang/Object;",
          false);
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PRIVATE, "a", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(4, 9);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 3);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
