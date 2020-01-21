// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b147865212;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

// Generate a class file with the following code, line number table,
// and local variable table.
//
//  public static final java.lang.String box(int);
//    descriptor: (I)Ljava/lang/String;
//    flags: ACC_PUBLIC, ACC_STATIC, ACC_FINAL
//    Code:
//      stack=1, locals=3, args_size=1
//         0: nop
//         1: ldc           #9                  // String OK
//         3: astore_1
//         4: nop
//         5: iload_0
//         6: istore_2
//         7: nop
//         8: nop
//         9: iload_0
//        10: istore_2
//        11: nop
//        12: aload_1
//        13: areturn
//        14: astore_1
//        15: nop
//        16: iload_0
//        17: istore_2
//        18: nop
//        19: nop
//        20: iload_0
//        21: istore_2
//        22: nop
//        23: aload_1
//        24: athrow
//      Exception table:
//         from    to  target type
//             0     4    14   any
//            14    15    14   any
//      StackMapTable: number_of_entries = 1
//        frame_type = 78 /* same_locals_1_stack_item */
//          stack = [ class java/lang/Throwable ]
//      LineNumberTable:
//        line 2: 0
//        line 3: 1
//        line 5: 4
//        line 6: 5
//        line 8: 8
//        line 9: 9
//        line 10: 11
//        line 3: 13
//        line 12: 14
//        line 5: 15
//        line 6: 16
//        line 8: 19
//        line 9: 20
//        line 10: 22
//      LocalVariableTable:
//        Start  Length  Slot  Name   Signature
//            7       1     2     z   I
//           11       1     2     z   I
//           18       1     2     z   I
//           22       1     2     z   I
//            0      25     0     x   I
public class Flaf2Dump implements Opcodes {

  public static byte[] dump() throws Exception {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_6, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, "FlafKt", null, "java/lang/Object", null);

    classWriter.visitSource("Flaf.kt", null);

    {
      annotationVisitor0 = classWriter.visitAnnotation("Lkotlin/Metadata;", true);
      annotationVisitor0.visit("mv", new int[] {1, 1, 17});
      annotationVisitor0.visit("bv", new int[] {1, 0, 3});
      annotationVisitor0.visit("k", new Integer(2));
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d1");
        annotationVisitor1.visit(
            null,
            "\u0000\u0006\n\u0000\n\u0002\u0010\u000e\u001a\u0006\u0010\u0000\u001a\u00020\u0001");
        annotationVisitor1.visitEnd();
      }
      {
        AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d2");
        annotationVisitor1.visit(null, "box");
        annotationVisitor1.visit(null, "");
        annotationVisitor1.visitEnd();
      }
      annotationVisitor0.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "box", "()Ljava/lang/String;", null, null);
      {
        annotationVisitor0 =
            methodVisitor.visitAnnotation("Lorg/jetbrains/annotations/NotNull;", false);
        annotationVisitor0.visitEnd();
      }
      methodVisitor.visitCode();
      Label label0 = new Label();
      Label label1 = new Label();
      Label label2 = new Label();
      methodVisitor.visitTryCatchBlock(label0, label1, label2, null);
      Label label3 = new Label();
      methodVisitor.visitTryCatchBlock(label2, label3, label2, null);
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(2, label0);
      methodVisitor.visitInsn(NOP);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(3, label4);
      methodVisitor.visitLdcInsn("OK");
      methodVisitor.visitVarInsn(ASTORE, 0);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(5, label1);
      methodVisitor.visitInsn(NOP);
      Label label5 = new Label();
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(6, label5);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitVarInsn(ISTORE, 1);
      Label label6 = new Label();
      methodVisitor.visitLabel(label6);
      methodVisitor.visitInsn(NOP);
      Label label7 = new Label();
      methodVisitor.visitLabel(label7);
      methodVisitor.visitLineNumber(8, label7);
      methodVisitor.visitInsn(NOP);
      Label label8 = new Label();
      methodVisitor.visitLabel(label8);
      methodVisitor.visitLineNumber(9, label8);
      methodVisitor.visitInsn(ICONST_4);
      methodVisitor.visitVarInsn(ISTORE, 1);
      Label label9 = new Label();
      methodVisitor.visitLabel(label9);
      methodVisitor.visitInsn(NOP);
      Label label10 = new Label();
      methodVisitor.visitLabel(label10);
      methodVisitor.visitVarInsn(ALOAD, 0);
      Label label11 = new Label();
      methodVisitor.visitLabel(label11);
      methodVisitor.visitLineNumber(3, label11);
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(11, label2);
      methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
      methodVisitor.visitVarInsn(ASTORE, 0);
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(5, label3);
      methodVisitor.visitInsn(NOP);
      Label label12 = new Label();
      methodVisitor.visitLabel(label12);
      methodVisitor.visitLineNumber(6, label12);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitVarInsn(ISTORE, 1);
      Label label13 = new Label();
      methodVisitor.visitLabel(label13);
      methodVisitor.visitInsn(NOP);
      Label label14 = new Label();
      methodVisitor.visitLabel(label14);
      methodVisitor.visitLineNumber(8, label14);
      methodVisitor.visitInsn(NOP);
      Label label15 = new Label();
      methodVisitor.visitLabel(label15);
      methodVisitor.visitLineNumber(9, label15);
      methodVisitor.visitInsn(ICONST_4);
      methodVisitor.visitVarInsn(ISTORE, 1);
      Label label16 = new Label();
      methodVisitor.visitLabel(label16);
      methodVisitor.visitInsn(NOP);
      Label label17 = new Label();
      methodVisitor.visitLabel(label17);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitInsn(ATHROW);
      methodVisitor.visitLocalVariable("z", "I", null, label6, label7, 1);
      methodVisitor.visitLocalVariable("z", "I", null, label9, label10, 1);
      methodVisitor.visitLocalVariable("z", "I", null, label13, label14, 1);
      methodVisitor.visitLocalVariable("z", "I", null, label16, label17, 1);
      methodVisitor.visitMaxs(1, 2);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
