// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b139991218;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class Main implements Opcodes {

  // Generated from the following kotlin code:
  // private var COUNT = 11
  //
  // private fun next() = "${COUNT++}"
  //
  // data class Alpha(val id: String = next())
  //
  // fun <T> consume(t: T, l: (t: T) -> String) = l(t)
  //
  // fun main(args: Array<String>) {
  //     test({ it.id }, Alpha()) <-- This is Lambda1
  //     test({ it.id }, Alpha()) <-- This is Lambda2
  // }
  //
  // private fun <T> test(l : (T) -> String, t : T) {
  //     println(l(t))
  // }
  public static byte[] dump() {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_8,
        ACC_PUBLIC | ACC_FINAL | ACC_SUPER,
        "com/android/tools/r8/naming/b139991218/Main",
        null,
        "java/lang/Object",
        null);

    classWriter.visitSource(
        "main.kt",
        "SMAP\n"
            + "main.kt\n"
            + "Kotlin\n"
            + "*S Kotlin\n"
            + "*F\n"
            + "+ 1 main.kt\n"
            + "com/android/tools/r8/naming/b139991218/Main\n"
            + "*L\n"
            + "1#1,25:1\n"
            + "*E\n");

    {
      annotationVisitor0 = classWriter.visitAnnotation("Lkotlin/Metadata;", true);
      annotationVisitor0.visit("mv", new int[] {1, 1, 13});
      annotationVisitor0.visit("bv", new int[] {1, 0, 3});
      annotationVisitor0.visit("k", new Integer(2));
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d1");
        annotationVisitor1.visit(
            null,
            "\u0000*\n"
                + "\u0000\n"
                + "\u0002\u0010\u0008\n"
                + "\u0000\n"
                + "\u0002\u0010\u000e\n"
                + "\u0002\u0008\u0003\n"
                + "\u0002\u0018\u0002\n"
                + "\u0002\u0018\u0002\n"
                + "\u0002\u0008\u0003\n"
                + "\u0002\u0010\u0002\n"
                + "\u0000\n"
                + "\u0002\u0010\u0011\n"
                + "\u0002\u0008\u0004\u001a<\u0010\u0002\u001a\u00020\u0003\"\u0004\u0008\u0000\u0010\u00042\u0006\u0010\u0005\u001a\u0002H\u00042!\u0010\u0006\u001a\u001d\u0012\u0013\u0012\u0011H\u0004\u00a2\u0006\u000c\u0008\u0008\u0012\u0008\u0008\u0009\u0012\u0004\u0008\u0008(\u0005\u0012\u0004\u0012\u00020\u00030\u0007\u00a2\u0006\u0002\u0010\n"
                + "\u001a\u0019\u0010\u000b\u001a\u00020\u000c2\u000c\u0010\r"
                + "\u001a\u0008\u0012\u0004\u0012\u00020\u00030\u000e\u00a2\u0006\u0002\u0010\u000f\u001a\u0008\u0010\u0010\u001a\u00020\u0003H\u0002\u001a/\u0010\u0011\u001a\u00020\u000c\"\u0004\u0008\u0000\u0010\u00042\u0012\u0010\u0006\u001a\u000e\u0012\u0004\u0012\u0002H\u0004\u0012\u0004\u0012\u00020\u00030\u00072\u0006\u0010\u0005\u001a\u0002H\u0004H\u0002\u00a2\u0006\u0002\u0010\u0012\"\u000e\u0010\u0000\u001a\u00020\u0001X\u0082\u000e\u00a2\u0006\u0002\n"
                + "\u0000");
        annotationVisitor1.visitEnd();
      }
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d2");
        annotationVisitor1.visit(null, "COUNT");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visit(null, "consume");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visit(null, "T");
        annotationVisitor1.visit(null, "t");
        annotationVisitor1.visit(null, "l");
        annotationVisitor1.visit(null, "Lkotlin/Function1;");
        annotationVisitor1.visit(null, "Lkotlin/ParameterName;");
        annotationVisitor1.visit(null, "name");
        annotationVisitor1.visit(
            null, "(Ljava/lang/Object;Lkotlin/jvm/functions/Function1;)Ljava/lang/String;");
        annotationVisitor1.visit(null, "main");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visit(null, "args");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visit(null, "([Ljava/lang/String;)V");
        annotationVisitor1.visit(null, "next");
        annotationVisitor1.visit(null, "test");
        annotationVisitor1.visit(null, "(Lkotlin/jvm/functions/Function1;Ljava/lang/Object;)V");
        annotationVisitor1.visitEnd();
      }
      annotationVisitor0.visitEnd();
    }
    classWriter.visitInnerClass(
        "com/android/tools/r8/naming/b139991218/Lambda1", null, null, ACC_FINAL | ACC_STATIC);

    classWriter.visitInnerClass(
        "com/android/tools/r8/naming/b139991218/Lambda2", null, null, ACC_FINAL | ACC_STATIC);

    {
      fieldVisitor = classWriter.visitField(ACC_PRIVATE | ACC_STATIC, "COUNT", "I", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PRIVATE | ACC_FINAL | ACC_STATIC, "next", "()Ljava/lang/String;", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(9, label0);
      methodVisitor.visitFieldInsn(
          GETSTATIC, "com/android/tools/r8/naming/b139991218/Main", "COUNT", "I");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ISTORE, 0);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitInsn(IADD);
      methodVisitor.visitFieldInsn(
          PUTSTATIC, "com/android/tools/r8/naming/b139991218/Main", "COUNT", "I");
      methodVisitor.visitVarInsn(ILOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false);
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_FINAL | ACC_STATIC,
              "consume",
              "(Ljava/lang/Object;Lkotlin/jvm/functions/Function1;)Ljava/lang/String;",
              "<T:Ljava/lang/Object;>(TT;Lkotlin/jvm/functions/Function1<-TT;Ljava/lang/String;>;)Ljava/lang/String;",
              null);
      {
        annotationVisitor0 =
            methodVisitor.visitAnnotation("Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitAnnotableParameterCount(2, false);
      {
        annotationVisitor0 =
            methodVisitor.visitParameterAnnotation(1, "Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitLdcInsn("l");
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "kotlin/jvm/internal/Intrinsics",
          "checkParameterIsNotNull",
          "(Ljava/lang/Object;Ljava/lang/String;)V",
          false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(13, label1);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE,
          "kotlin/jvm/functions/Function1",
          "invoke",
          "(Ljava/lang/Object;)Ljava/lang/Object;",
          true);
      methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
      methodVisitor.visitInsn(ARETURN);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLocalVariable("t", "Ljava/lang/Object;", null, label0, label2, 0);
      methodVisitor.visitLocalVariable(
          "l", "Lkotlin/jvm/functions/Function1;", null, label0, label2, 1);
      methodVisitor.visitMaxs(2, 2);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      methodVisitor.visitAnnotableParameterCount(1, false);
      {
        annotationVisitor0 =
            methodVisitor.visitParameterAnnotation(0, "Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitLdcInsn("args");
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "kotlin/jvm/internal/Intrinsics",
          "checkParameterIsNotNull",
          "(Ljava/lang/Object;Ljava/lang/String;)V",
          false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(16, label1);
      methodVisitor.visitFieldInsn(
          GETSTATIC,
          "com/android/tools/r8/naming/b139991218/Lambda1",
          "INSTANCE",
          "Lcom/android/tools/r8/naming/b139991218/Lambda1;");
      methodVisitor.visitTypeInsn(CHECKCAST, "kotlin/jvm/functions/Function1");
      methodVisitor.visitTypeInsn(NEW, "com/android/tools/r8/naming/b139991218/Alpha");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "com/android/tools/r8/naming/b139991218/Alpha",
          "<init>",
          "(Ljava/lang/String;ILkotlin/jvm/internal/DefaultConstructorMarker;)V",
          false);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/naming/b139991218/Main",
          "test",
          "(Lkotlin/jvm/functions/Function1;Ljava/lang/Object;)V",
          false);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(17, label2);
      methodVisitor.visitFieldInsn(
          GETSTATIC,
          "com/android/tools/r8/naming/b139991218/Lambda2",
          "INSTANCE",
          "Lcom/android/tools/r8/naming/b139991218/Lambda2;");
      methodVisitor.visitTypeInsn(CHECKCAST, "kotlin/jvm/functions/Function1");
      methodVisitor.visitTypeInsn(NEW, "com/android/tools/r8/naming/b139991218/Alpha");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL,
          "com/android/tools/r8/naming/b139991218/Alpha",
          "<init>",
          "(Ljava/lang/String;ILkotlin/jvm/internal/DefaultConstructorMarker;)V",
          false);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/naming/b139991218/Main",
          "test",
          "(Lkotlin/jvm/functions/Function1;Ljava/lang/Object;)V",
          false);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(18, label3);
      methodVisitor.visitInsn(RETURN);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLocalVariable("args", "[Ljava/lang/String;", null, label0, label4, 0);
      methodVisitor.visitMaxs(6, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PRIVATE | ACC_FINAL | ACC_STATIC,
              "test",
              "(Lkotlin/jvm/functions/Function1;Ljava/lang/Object;)V",
              "<T:Ljava/lang/Object;>(Lkotlin/jvm/functions/Function1<-TT;Ljava/lang/String;>;TT;)V",
              null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(21, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKEINTERFACE,
          "kotlin/jvm/functions/Function1",
          "invoke",
          "(Ljava/lang/Object;)Ljava/lang/Object;",
          true);
      methodVisitor.visitVarInsn(ASTORE, 2);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(22, label1);
      methodVisitor.visitInsn(RETURN);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLocalVariable(
          "l", "Lkotlin/jvm/functions/Function1;", null, label0, label2, 0);
      methodVisitor.visitLocalVariable("t", "Ljava/lang/Object;", null, label0, label2, 1);
      methodVisitor.visitMaxs(2, 3);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(7, label0);
      methodVisitor.visitIntInsn(BIPUSH, 11);
      methodVisitor.visitFieldInsn(
          PUTSTATIC, "com/android/tools/r8/naming/b139991218/Main", "COUNT", "I");
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 0);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_FINAL | ACC_STATIC | ACC_SYNTHETIC,
              "access$next",
              "()Ljava/lang/String;",
              null,
              null);
      {
        annotationVisitor0 =
            methodVisitor.visitAnnotation("Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(1, label0);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/naming/b139991218/Main",
          "next",
          "()Ljava/lang/String;",
          false);
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(1, 0);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
