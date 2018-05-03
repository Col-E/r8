// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import org.objectweb.asm.*;

public class KotlinRingBufferDump implements Opcodes {

  public static final String INTERNAL_NAME = "kotlin/collections/RingBuffer";
  public static final String DESCRIPTOR = "L" + INTERNAL_NAME + ";";
  public static final String CLASS_NAME = "kotlin.collections.RingBuffer";

  public static byte[] dump() throws Exception {

    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    AnnotationVisitor av0;

    String superName = "java/lang/Object"; // "kotlin/collections/AbstractList";
    String signature =
        null; // "<T:Ljava/lang/Object;>Lkotlin/collections/AbstractList<TT;>;Ljava/util/RandomAccess;";
    cw.visit(
        V1_6,
        ACC_FINAL + ACC_SUPER + ACC_PUBLIC,
        INTERNAL_NAME,
        signature,
        superName,
        new String[] {"java/util/RandomAccess"});

    {
      av0 = cw.visitAnnotation("Lkotlin/Metadata;", true);
      av0.visit("mv", new int[] {1, 1, 9});
      av0.visit("bv", new int[] {1, 0, 2});
      av0.visit("k", new Integer(1));
      {
        AnnotationVisitor av1 = av0.visitArray("d1");
        av1.visit(
            null,
            "\u0000>\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0008\n\u0002\u0008\u0002\n\u0002\u0010\u0011\n\u0002\u0010\u0000\n\u0002\u0008\u0009\n\u0002\u0010\u0002\n\u0002\u0008\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010(\n\u0002\u0008\u000c\u0008\u0002\u0018\u0000*\u0004\u0008\u0000\u0010\u00012\u0008\u0012\u0004\u0012\u0002H\u00010\u00022\u00060\u0003j\u0002`\u0004B\r\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0002\u0010\u0007J\u0013\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00028\u0000\u00a2\u0006\u0002\u0010\u0016J\u0016\u0010\u0017\u001a\u00028\u00002\u0006\u0010\u0018\u001a\u00020\u0006H\u0096\u0002\u00a2\u0006\u0002\u0010\u0019J\u0006\u0010\u001a\u001a\u00020\u001bJ\u000f\u0010\u001c\u001a\u0008\u0012\u0004\u0012\u00028\u00000\u001dH\u0096\u0002J\u000e\u0010\u001e\u001a\u00020\u00142\u0006\u0010\u001f\u001a\u00020\u0006J\u0015\u0010 \u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\n0\u0009H\u0014\u00a2\u0006\u0002\u0010!J'\u0010 \u001a\u0008\u0012\u0004\u0012\u0002H\u00010\u0009\"\u0004\u0008\u0001\u0010\u00012\u000c\u0010\"\u001a\u0008\u0012\u0004\u0012\u0002H\u00010\u0009H\u0015\u00a2\u0006\u0002\u0010#J9\u0010$\u001a\u00020\u0014\"\u0004\u0008\u0001\u0010\u0001*\u0008\u0012\u0004\u0012\u0002H\u00010\u00092\u0006\u0010\u0015\u001a\u0002H\u00012\u0008\u0008\u0002\u0010%\u001a\u00020\u00062\u0008\u0008\u0002\u0010&\u001a\u00020\u0006H\u0002\u00a2\u0006\u0002\u0010'J\u0015\u0010(\u001a\u00020\u0006*\u00020\u00062\u0006\u0010\u001f\u001a\u00020\u0006H\u0083\u0008R\u0018\u0010\u0008\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\n0\u0009X\u0082\u0004\u00a2\u0006\u0004\n\u0002\u0010\u000bR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\u0008\n\u0000\u001a\u0004\u0008\u000c\u0010\rR$\u0010\u000f\u001a\u00020\u00062\u0006\u0010\u000e\u001a\u00020\u0006@RX\u0096\u000e\u00a2\u0006\u000e\n\u0000\u001a\u0004\u0008\u0010\u0010\r\"\u0004\u0008\u0011\u0010\u0007R\u000e\u0010\u0012\u001a\u00020\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006)");
        av1.visitEnd();
      }
      {
        AnnotationVisitor av1 = av0.visitArray("d2");
        av1.visit(null, DESCRIPTOR);
        av1.visit(null, "T");
        av1.visit(null, "Lkotlin/collections/AbstractList;");
        av1.visit(null, "Ljava/util/RandomAccess;");
        av1.visit(null, "Lkotlin/collections/RandomAccess;");
        av1.visit(null, "capacity");
        av1.visit(null, "");
        av1.visit(null, "(I)V");
        av1.visit(null, "buffer");
        av1.visit(null, "");
        av1.visit(null, "");
        av1.visit(null, "[Ljava/lang/Object;");
        av1.visit(null, "getCapacity");
        av1.visit(null, "()I");
        av1.visit(null, "<set-?>");
        av1.visit(null, "size");
        av1.visit(null, "getSize");
        av1.visit(null, "setSize");
        av1.visit(null, "startIndex");
        av1.visit(null, "add");
        av1.visit(null, "");
        av1.visit(null, "element");
        av1.visit(null, "(Ljava/lang/Object;)V");
        av1.visit(null, "get");
        av1.visit(null, "index");
        av1.visit(null, "(I)Ljava/lang/Object;");
        av1.visit(null, "isFull");
        av1.visit(null, "");
        av1.visit(null, "iterator");
        av1.visit(null, "");
        av1.visit(null, "removeFirst");
        av1.visit(null, "n");
        av1.visit(null, "toArray");
        av1.visit(null, "()[Ljava/lang/Object;");
        av1.visit(null, "array");
        av1.visit(null, "([Ljava/lang/Object;)[Ljava/lang/Object;");
        av1.visit(null, "fill");
        av1.visit(null, "fromIndex");
        av1.visit(null, "toIndex");
        av1.visit(null, "([Ljava/lang/Object;Ljava/lang/Object;II)V");
        av1.visit(null, "forward");
        av1.visit(null, "kotlin-stdlib");
        av1.visitEnd();
      }
      av0.visitEnd();
    }
    cw.visitInnerClass(
        INTERNAL_NAME + "$iterator$1", null, null, ACC_PUBLIC + ACC_FINAL + ACC_STATIC);

    {
      fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, "buffer", "[Ljava/lang/Object;", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_PRIVATE, "startIndex", "I", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_PRIVATE, "size", "I", null, null);
      fv.visitEnd();
    }
    {
      fv = cw.visitField(ACC_PRIVATE + ACC_FINAL, "capacity", "I", null, null);
      fv.visitEnd();
    }
    methodAdd(cw);
    methodMain(cw);
    cw.visitEnd();

    return cw.toByteArray();
  }

  private static void methodMain(ClassWriter cw) {
    MethodVisitor mv =
        cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
    mv.visitCode();
    mv.visitInsn(RETURN);
    mv.visitMaxs(0, 1);
    mv.visitEnd();
  }

  private static void methodAdd(ClassWriter cw) {
    MethodVisitor mv =
        cw.visitMethod(ACC_PUBLIC + ACC_FINAL, "add", "(Ljava/lang/Object;)V", "(TT;)V", null);
    Label[] labels = new Label[8];
    for (int i = 0; i < labels.length; i++) {
      labels[i] = new Label();
    }
    mv.visitCode();
    mv.visitLabel(labels[0]);
    mv.visitLineNumber(169, labels[0]);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKEVIRTUAL, INTERNAL_NAME, "isFull", "()Z", false);
    mv.visitJumpInsn(IFEQ, labels[1]);
    mv.visitLabel(labels[2]);
    mv.visitLineNumber(170, labels[2]);
    mv.visitTypeInsn(NEW, "java/lang/IllegalStateException");
    mv.visitInsn(DUP);
    mv.visitLdcInsn("ring buffer is full");
    mv.visitMethodInsn(
        INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false);
    mv.visitTypeInsn(CHECKCAST, "java/lang/Throwable");
    mv.visitInsn(ATHROW);
    mv.visitLabel(labels[1]);
    mv.visitLineNumber(173, labels[1]);
    mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitFieldInsn(GETFIELD, INTERNAL_NAME, "buffer", "[Ljava/lang/Object;");
    mv.visitVarInsn(ALOAD, 0);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitFieldInsn(GETFIELD, INTERNAL_NAME, "startIndex", "I");
    mv.visitVarInsn(ISTORE, 3);
    mv.visitVarInsn(ASTORE, 2);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitMethodInsn(INVOKEVIRTUAL, INTERNAL_NAME, "size", "()I", false);
    mv.visitVarInsn(ISTORE, 4);
    mv.visitLabel(labels[3]);
    mv.visitLineNumber(212, labels[3]);
    mv.visitVarInsn(ILOAD, 3);
    mv.visitVarInsn(ILOAD, 4);
    mv.visitInsn(IADD);
    mv.visitVarInsn(ALOAD, 2);
    mv.visitMethodInsn(INVOKEVIRTUAL, INTERNAL_NAME, "getCapacity", "()I", false);
    mv.visitInsn(IREM);
    mv.visitLabel(labels[4]);
    mv.visitVarInsn(ALOAD, 1);
    mv.visitInsn(AASTORE);
    mv.visitLabel(labels[5]);
    mv.visitLineNumber(174, labels[5]);
    mv.visitVarInsn(ALOAD, 0);
    mv.visitInsn(DUP);
    mv.visitMethodInsn(INVOKEVIRTUAL, INTERNAL_NAME, "size", "()I", false);
    mv.visitInsn(DUP);
    mv.visitVarInsn(ISTORE, 2);
    mv.visitInsn(ICONST_1);
    mv.visitInsn(IADD);
    mv.visitMethodInsn(INVOKESPECIAL, INTERNAL_NAME, "setSize", "(I)V", false);
    mv.visitLabel(labels[6]);
    mv.visitLineNumber(175, labels[6]);
    mv.visitInsn(RETURN);
    mv.visitLabel(labels[7]);
    mv.visitLocalVariable("this_$iv", DESCRIPTOR, null, labels[3], labels[4], 2);
    mv.visitLocalVariable("$receiver$iv", "I", null, labels[3], labels[4], 3);
    mv.visitLocalVariable("n$iv", "I", null, labels[3], labels[4], 4);
    mv.visitLocalVariable("$i$f$forward", "I", null, labels[3], labels[4], 5);
    mv.visitLocalVariable("this", DESCRIPTOR, null, labels[0], labels[7], 0);
    mv.visitLocalVariable("element", "Ljava/lang/Object;", null, labels[0], labels[7], 1);
    mv.visitMaxs(3, 6);
    mv.visitEnd();
  }
}
