// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ArrayDimensionGreaterThanSevenTestDump implements Opcodes {

  public static byte[] dump() throws Exception {

    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    FieldVisitor fv;
    MethodVisitor mv;
    AnnotationVisitor av0;

    cw.visit(
        V1_8,
        ACC_PUBLIC + ACC_SUPER,
        "com/android/tools/r8/debug/ArrayDimensionGreaterThanSevenTest",
        null,
        "java/lang/Object",
        null);

    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "foo", "(I)F", null, null);
      mv.visitCode();
      Label l0 = new Label();
      Label l1 = new Label();
      Label l2 = new Label();
      mv.visitTryCatchBlock(l0, l1, l2, "java/lang/NullPointerException");
      Label l3 = new Label();
      Label l4 = new Label();
      Label l5 = new Label();
      mv.visitTryCatchBlock(l3, l4, l5, "java/lang/RuntimeException");
      mv.visitLabel(l3);
      mv.visitInsn(ICONST_1);
      mv.visitIntInsn(NEWARRAY, T_FLOAT);
      mv.visitInsn(DUP);
      mv.visitInsn(ICONST_0);
      mv.visitLdcInsn(new Float("42.0"));
      mv.visitInsn(FASTORE);
      mv.visitVarInsn(ASTORE, 1);
      mv.visitInsn(ICONST_1);
      mv.visitTypeInsn(ANEWARRAY, "[F");
      mv.visitInsn(DUP);
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitInsn(AASTORE);
      mv.visitVarInsn(ASTORE, 2);
      mv.visitInsn(ICONST_1);
      mv.visitTypeInsn(ANEWARRAY, "[[F");
      mv.visitInsn(DUP);
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ALOAD, 2);
      mv.visitInsn(AASTORE);
      mv.visitVarInsn(ASTORE, 3);
      mv.visitInsn(ICONST_1);
      mv.visitTypeInsn(ANEWARRAY, "[[[F");
      mv.visitInsn(DUP);
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitInsn(AASTORE);
      mv.visitVarInsn(ASTORE, 4);
      mv.visitInsn(ICONST_1);
      mv.visitTypeInsn(ANEWARRAY, "[[[[F");
      mv.visitInsn(DUP);
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ALOAD, 4);
      mv.visitInsn(AASTORE);
      mv.visitVarInsn(ASTORE, 5);
      mv.visitInsn(ICONST_1);
      mv.visitTypeInsn(ANEWARRAY, "[[[[[F");
      mv.visitInsn(DUP);
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ALOAD, 5);
      mv.visitInsn(AASTORE);
      mv.visitVarInsn(ASTORE, 6);
      mv.visitInsn(ICONST_1);
      mv.visitTypeInsn(ANEWARRAY, "[[[[[[F");
      mv.visitInsn(DUP);
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ALOAD, 6);
      mv.visitInsn(AASTORE);
      mv.visitVarInsn(ASTORE, 7);
      mv.visitInsn(ICONST_1);
      mv.visitTypeInsn(ANEWARRAY, "[[[[[[[F");
      mv.visitInsn(DUP);
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ALOAD, 7);
      mv.visitInsn(AASTORE);
      mv.visitVarInsn(ASTORE, 8);
      Label l6 = new Label();
      mv.visitLabel(l6);
      // mv.visitFrame(Opcodes.F_FULL, 9, new Object[] {Opcodes.INTEGER, "[F", "[[F", "[[[F",
      // "[[[[F", "[[[[[F", "[[[[[[F", "[[[[[[[F", "[[[[[[[[F"}, 0, new Object[] {});
      mv.visitFrame(
          Opcodes.F_NEW,
          9,
          new Object[] {
            Opcodes.INTEGER,
            "[F",
            "[[F",
            "[[[F",
            "[[[[F",
            "[[[[[F",
            "[[[[[[F",
            "[[[[[[[F",
            "[[[[[[[[F"
          },
          0,
          new Object[] {});
      mv.visitVarInsn(ILOAD, 0);
      mv.visitIincInsn(0, -1);
      mv.visitJumpInsn(IFLE, l4);
      mv.visitLabel(l0);
      mv.visitVarInsn(ILOAD, 0);
      Label l7 = new Label();
      mv.visitJumpInsn(IFNE, l7);
      mv.visitVarInsn(ALOAD, 8);
      Label l8 = new Label();
      mv.visitJumpInsn(GOTO, l8);
      mv.visitLabel(l7);
      // mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFrame(
          Opcodes.F_NEW,
          9,
          new Object[] {
            Opcodes.INTEGER,
            "[F",
            "[[F",
            "[[[F",
            "[[[[F",
            "[[[[[F",
            "[[[[[[F",
            "[[[[[[[F",
            "[[[[[[[[F"
          },
          0,
          new Object[] {});
      mv.visitInsn(ACONST_NULL);
      mv.visitTypeInsn(CHECKCAST, "[[[[[[[[F");
      mv.visitLabel(l8);
      // mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"[[[[[[[[F"});
      mv.visitFrame(
          Opcodes.F_NEW,
          9,
          new Object[] {
            Opcodes.INTEGER,
            "[F",
            "[[F",
            "[[[F",
            "[[[[F",
            "[[[[[F",
            "[[[[[[F",
            "[[[[[[[F",
            "[[[[[[[[F"
          },
          1,
          new Object[] {"[[[[[[[[F"});
      mv.visitVarInsn(ASTORE, 8);
      mv.visitVarInsn(ILOAD, 0);
      mv.visitInsn(ICONST_1);
      Label l9 = new Label();
      mv.visitJumpInsn(IF_ICMPNE, l9);
      mv.visitVarInsn(ALOAD, 8);
      mv.visitInsn(ICONST_1);
      mv.visitInsn(AALOAD);
      Label l10 = new Label();
      mv.visitJumpInsn(GOTO, l10);
      mv.visitLabel(l9);
      // mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFrame(
          Opcodes.F_NEW,
          9,
          new Object[] {
            Opcodes.INTEGER,
            "[F",
            "[[F",
            "[[[F",
            "[[[[F",
            "[[[[[F",
            "[[[[[[F",
            "[[[[[[[F",
            "[[[[[[[[F"
          },
          0,
          new Object[] {});
      mv.visitVarInsn(ALOAD, 8);
      mv.visitInsn(ICONST_0);
      mv.visitInsn(AALOAD);
      mv.visitLabel(l10);
      // mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"[[[[[[[F"});
      mv.visitFrame(
          Opcodes.F_NEW,
          9,
          new Object[] {
            Opcodes.INTEGER,
            "[F",
            "[[F",
            "[[[F",
            "[[[[F",
            "[[[[[F",
            "[[[[[[F",
            "[[[[[[[F",
            "[[[[[[[[F"
          },
          1,
          new Object[] {"[[[[[[[F"});
      mv.visitVarInsn(ASTORE, 7);
      mv.visitVarInsn(ILOAD, 0);
      mv.visitInsn(ICONST_2);
      Label l11 = new Label();
      mv.visitJumpInsn(IF_ICMPNE, l11);
      mv.visitVarInsn(ALOAD, 7);
      mv.visitInsn(ICONST_1);
      mv.visitInsn(AALOAD);
      Label l12 = new Label();
      mv.visitJumpInsn(GOTO, l12);
      mv.visitLabel(l11);
      // mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFrame(
          Opcodes.F_NEW,
          9,
          new Object[] {
            Opcodes.INTEGER,
            "[F",
            "[[F",
            "[[[F",
            "[[[[F",
            "[[[[[F",
            "[[[[[[F",
            "[[[[[[[F",
            "[[[[[[[[F"
          },
          0,
          new Object[] {});
      mv.visitVarInsn(ALOAD, 7);
      mv.visitInsn(ICONST_0);
      mv.visitInsn(AALOAD);
      mv.visitLabel(l12);
      // mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"[[[[[[F"});
      mv.visitFrame(
          Opcodes.F_NEW,
          9,
          new Object[] {
            Opcodes.INTEGER,
            "[F",
            "[[F",
            "[[[F",
            "[[[[F",
            "[[[[[F",
            "[[[[[[F",
            "[[[[[[[F",
            "[[[[[[[[F"
          },
          1,
          new Object[] {"[[[[[[F"});
      mv.visitVarInsn(ASTORE, 6);
      mv.visitVarInsn(ILOAD, 0);
      mv.visitInsn(ICONST_3);
      Label l13 = new Label();
      mv.visitJumpInsn(IF_ICMPNE, l13);
      mv.visitVarInsn(ALOAD, 6);
      mv.visitInsn(ICONST_1);
      mv.visitInsn(AALOAD);
      Label l14 = new Label();
      mv.visitJumpInsn(GOTO, l14);
      mv.visitLabel(l13);
      // mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFrame(
          Opcodes.F_NEW,
          9,
          new Object[] {
            Opcodes.INTEGER,
            "[F",
            "[[F",
            "[[[F",
            "[[[[F",
            "[[[[[F",
            "[[[[[[F",
            "[[[[[[[F",
            "[[[[[[[[F"
          },
          0,
          new Object[] {});
      mv.visitVarInsn(ALOAD, 6);
      mv.visitInsn(ICONST_0);
      mv.visitInsn(AALOAD);
      mv.visitLabel(l14);
      // mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"[[[[[F"});
      mv.visitFrame(
          Opcodes.F_NEW,
          9,
          new Object[] {
            Opcodes.INTEGER,
            "[F",
            "[[F",
            "[[[F",
            "[[[[F",
            "[[[[[F",
            "[[[[[[F",
            "[[[[[[[F",
            "[[[[[[[[F"
          },
          1,
          new Object[] {"[[[[[F"});
      mv.visitVarInsn(ASTORE, 5);
      mv.visitVarInsn(ILOAD, 0);
      mv.visitInsn(ICONST_4);
      Label l15 = new Label();
      mv.visitJumpInsn(IF_ICMPNE, l15);
      mv.visitVarInsn(ALOAD, 5);
      mv.visitInsn(ICONST_1);
      mv.visitInsn(AALOAD);
      Label l16 = new Label();
      mv.visitJumpInsn(GOTO, l16);
      mv.visitLabel(l15);
      // mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFrame(
          Opcodes.F_NEW,
          9,
          new Object[] {
            Opcodes.INTEGER,
            "[F",
            "[[F",
            "[[[F",
            "[[[[F",
            "[[[[[F",
            "[[[[[[F",
            "[[[[[[[F",
            "[[[[[[[[F"
          },
          0,
          new Object[] {});
      mv.visitVarInsn(ALOAD, 5);
      mv.visitInsn(ICONST_0);
      mv.visitInsn(AALOAD);
      mv.visitLabel(l16);
      // mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"[[[[F"});
      mv.visitFrame(
          Opcodes.F_NEW,
          9,
          new Object[] {
            Opcodes.INTEGER,
            "[F",
            "[[F",
            "[[[F",
            "[[[[F",
            "[[[[[F",
            "[[[[[[F",
            "[[[[[[[F",
            "[[[[[[[[F"
          },
          1,
          new Object[] {"[[[[F"});
      mv.visitVarInsn(ASTORE, 4);
      mv.visitVarInsn(ILOAD, 0);
      mv.visitInsn(ICONST_5);
      Label l17 = new Label();
      mv.visitJumpInsn(IF_ICMPNE, l17);
      mv.visitVarInsn(ALOAD, 4);
      mv.visitInsn(ICONST_1);
      mv.visitInsn(AALOAD);
      Label l18 = new Label();
      mv.visitJumpInsn(GOTO, l18);
      mv.visitLabel(l17);
      // mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFrame(
          Opcodes.F_NEW,
          9,
          new Object[] {
            Opcodes.INTEGER,
            "[F",
            "[[F",
            "[[[F",
            "[[[[F",
            "[[[[[F",
            "[[[[[[F",
            "[[[[[[[F",
            "[[[[[[[[F"
          },
          0,
          new Object[] {});
      mv.visitVarInsn(ALOAD, 4);
      mv.visitInsn(ICONST_0);
      mv.visitInsn(AALOAD);
      mv.visitLabel(l18);
      // mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"[[[F"});
      mv.visitFrame(
          Opcodes.F_NEW,
          9,
          new Object[] {
            Opcodes.INTEGER,
            "[F",
            "[[F",
            "[[[F",
            "[[[[F",
            "[[[[[F",
            "[[[[[[F",
            "[[[[[[[F",
            "[[[[[[[[F"
          },
          1,
          new Object[] {"[[[F"});
      mv.visitVarInsn(ASTORE, 3);
      mv.visitVarInsn(ILOAD, 0);
      mv.visitIntInsn(BIPUSH, 6);
      Label l19 = new Label();
      mv.visitJumpInsn(IF_ICMPNE, l19);
      mv.visitVarInsn(ALOAD, 3);
      mv.visitInsn(ICONST_1);
      mv.visitInsn(AALOAD);
      Label l20 = new Label();
      mv.visitJumpInsn(GOTO, l20);
      mv.visitLabel(l19);
      // mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFrame(
          Opcodes.F_NEW,
          9,
          new Object[] {
            Opcodes.INTEGER,
            "[F",
            "[[F",
            "[[[F",
            "[[[[F",
            "[[[[[F",
            "[[[[[[F",
            "[[[[[[[F",
            "[[[[[[[[F"
          },
          0,
          new Object[] {});
      mv.visitVarInsn(ALOAD, 3);
      mv.visitInsn(ICONST_0);
      mv.visitInsn(AALOAD);
      mv.visitLabel(l20);
      // mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"[[F"});
      mv.visitFrame(
          Opcodes.F_NEW,
          9,
          new Object[] {
            Opcodes.INTEGER,
            "[F",
            "[[F",
            "[[[F",
            "[[[[F",
            "[[[[[F",
            "[[[[[[F",
            "[[[[[[[F",
            "[[[[[[[[F"
          },
          1,
          new Object[] {"[[F"});
      mv.visitVarInsn(ASTORE, 2);
      mv.visitVarInsn(ILOAD, 0);
      mv.visitIntInsn(BIPUSH, 7);
      Label l21 = new Label();
      mv.visitJumpInsn(IF_ICMPNE, l21);
      mv.visitVarInsn(ALOAD, 2);
      mv.visitInsn(ICONST_1);
      mv.visitInsn(AALOAD);
      Label l22 = new Label();
      mv.visitJumpInsn(GOTO, l22);
      mv.visitLabel(l21);
      // mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFrame(
          Opcodes.F_NEW,
          9,
          new Object[] {
            Opcodes.INTEGER,
            "[F",
            "[[F",
            "[[[F",
            "[[[[F",
            "[[[[[F",
            "[[[[[[F",
            "[[[[[[[F",
            "[[[[[[[[F"
          },
          0,
          new Object[] {});
      mv.visitVarInsn(ALOAD, 2);
      mv.visitInsn(ICONST_0);
      mv.visitInsn(AALOAD);
      mv.visitLabel(l22);
      // mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"[F"});
      mv.visitFrame(
          Opcodes.F_NEW,
          9,
          new Object[] {
            Opcodes.INTEGER,
            "[F",
            "[[F",
            "[[[F",
            "[[[[F",
            "[[[[[F",
            "[[[[[[F",
            "[[[[[[[F",
            "[[[[[[[[F"
          },
          1,
          new Object[] {"[F"});
      mv.visitVarInsn(ASTORE, 1);
      mv.visitLabel(l1);
      mv.visitJumpInsn(GOTO, l6);
      mv.visitLabel(l2);
      // mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]
      // {"java/lang/NullPointerException"});
      mv.visitFrame(
          Opcodes.F_NEW,
          9,
          new Object[] {
            Opcodes.INTEGER,
            "[F",
            "[[F",
            "[[[F",
            "[[[[F",
            "[[[[[F",
            "[[[[[[F",
            "[[[[[[[F",
            "[[[[[[[[F"
          },
          1,
          new Object[] {"java/lang/NullPointerException"});
      mv.visitVarInsn(ASTORE, 9);
      mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      mv.visitLdcInsn("null pointer");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      mv.visitJumpInsn(GOTO, l6);
      mv.visitLabel(l4);
      // mv.visitFrame(Opcodes.F_FULL, 1, new Object[] {Opcodes.INTEGER}, 0, new Object[] {});
      mv.visitFrame(Opcodes.F_NEW, 1, new Object[] {Opcodes.INTEGER}, 0, new Object[] {});
      Label l23 = new Label();
      mv.visitJumpInsn(GOTO, l23);
      mv.visitLabel(l5);
      // mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/RuntimeException"});
      mv.visitFrame(
          Opcodes.F_NEW,
          1,
          new Object[] {Opcodes.INTEGER},
          1,
          new Object[] {"java/lang/RuntimeException"});
      mv.visitVarInsn(ASTORE, 1);
      mv.visitLdcInsn(new Float("-1.0"));
      mv.visitInsn(FRETURN);
      mv.visitLabel(l23);
      // mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitFrame(Opcodes.F_NEW, 1, new Object[] {Opcodes.INTEGER}, 0, new Object[] {});
      mv.visitLdcInsn(new Float("42.0"));
      mv.visitInsn(FRETURN);
      mv.visitMaxs(4, 10);
      mv.visitEnd();
    }
    {
      mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      mv.visitCode();
      mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      mv.visitVarInsn(ALOAD, 0);
      mv.visitInsn(ARRAYLENGTH);
      mv.visitInsn(ICONST_1);
      mv.visitInsn(IADD);
      mv.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/debug/ArrayDimensionGreaterThanSevenTest",
          "foo",
          "(I)F",
          false);
      mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(F)V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(3, 1);
      mv.visitEnd();
    }
    cw.visitEnd();

    return cw.toByteArray();
  }
}
