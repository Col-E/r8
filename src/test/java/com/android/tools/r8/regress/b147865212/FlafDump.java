// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b147865212;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

// Generate a class file containing the following method, line number table,
// and local variable table. Conditionally add an extra entry to the line
// number table for the nop at instruction 5. When generateLineNumberForLocal
// is true, a `line 7: 5` entry is generated in the line number table
// making the local observable in the debugger.
//
//  public static final java.lang.String box();
//    descriptor: ()Ljava/lang/String;
//    flags: ACC_PUBLIC, ACC_STATIC, ACC_FINAL
//    Code:
//      stack=1, locals=1, args_size=0
//         0: nop
//         1: ldc           #11                 // String A
//         3: areturn
//         4: astore_0
//         5: nop
//         6: ldc           #13                 // String B
//         8: areturn
//      Exception table:
//         from    to  target type
//             0     4     4   Class java/lang/IllegalStateException
//      StackMapTable: number_of_entries = 1
//        frame_type = 68 /* same_locals_1_stack_item */
//          stack = [ class java/lang/IllegalStateException ]
//      LineNumberTable:
//        line 2: 0
//        line 3: 1
//        line 4: 4
//        line 5: 6
//        line 6: 6
//      LocalVariableTable:
//        Start  Length  Slot  Name   Signature
//            5       1     0     e   Ljava/lang/IllegalStateException;
public class FlafDump implements Opcodes {
  public static byte[] dump(boolean generateLineNumberForLocal) throws Exception {
    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;
    AnnotationVisitor annotationVisitor0;

    classWriter.visit(
        V1_6, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, "FlafKt", null, "java/lang/Object", null);

    classWriter.visitSource("Flaf.kt", null);

    {
      annotationVisitor0 = classWriter.visitAnnotation("Lkotlin/Metadata;", true);
      annotationVisitor0.visit("mv", new int[] {1, 1, 16});
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
      methodVisitor.visitTryCatchBlock(label0, label1, label1, "java/lang/IllegalStateException");
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(2, label0);
      methodVisitor.visitInsn(NOP);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(3, label2);
      methodVisitor.visitLdcInsn("A");
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(4, label1);
      methodVisitor.visitFrame(
          Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/IllegalStateException"});
      methodVisitor.visitVarInsn(ASTORE, 0);
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      if (generateLineNumberForLocal) {
        methodVisitor.visitLineNumber(7, label3);
      }
      methodVisitor.visitInsn(NOP);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(5, label4);
      methodVisitor.visitLineNumber(6, label4);
      methodVisitor.visitLdcInsn("B");
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitLocalVariable(
          "e", "Ljava/lang/IllegalStateException;", null, label3, label4, 0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
