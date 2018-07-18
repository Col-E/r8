// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debuginfo;

import java.util.*;
import org.objectweb.asm.*;

public class InliningWithoutPositionsTestSourceDump implements Opcodes {

  public enum Location {
    MAIN(123),
    FOO1(234),
    BAR(345),
    FOO2(456);
    final int line;

    Location(int line) {
      this.line = line;
    }
  };

  /*
  This generator creates the class 'InliningWithoutPositionsTestSource' with three methods,
  'main', calling 'foo', which is calling 'bar'.

  Depending on the mainPos, foo1Pos, barPos and foo2Pos parameters, we insert line numbers in
  'main', in 'foo' before calling 'bar' (marked foo1), in 'foo' after calling 'bar' (marked foo2)
  and in 'bar'. The methods contain no other line numbers.

  The throwLocation parameter determines at which location will be an exception thrown.

  The generator code is based on the ASMifier dump of the following class:

      package com.android.tools.r8.debuginfo;

      public class InliningWithoutPositionsTestSource {

        private static void throwingBar() {
          throw new RuntimeException("<BAR-exception>");
        }

        private static void printingBar() {
          System.err.println("<in-BAR>");
        }

        private static void foo(boolean b) {
          if (b) throw new RuntimeException("<FOO1-exception>");
          throwingBar();
        }

        private static void foo2() {
          throwingBar();
          throw new RuntimeException("<FOO2-exception>");
        }

        public static void main(String[] args) {
          foo(true);
        }
      }
  */
  public static byte[] dump(
      boolean mainPos, boolean foo1Pos, boolean barPos, boolean foo2Pos, Location throwLocation) {
    ClassWriter cw = new ClassWriter(0);
    FieldVisitor fv;
    MethodVisitor mv;
    AnnotationVisitor av0;

    cw.visit(
        V1_8,
        ACC_PUBLIC + ACC_SUPER,
        "com/android/tools/r8/debuginfo/InliningWithoutPositionsTestSource",
        null,
        "java/lang/Object",
        null);

    cw.visitSource("InliningWithoutPositionsTestSource.java", null);

    {
      mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      mv.visitCode();
      Label l0 = new Label();
      mv.visitLabel(l0);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      Label l1 = new Label();
      mv.visitLabel(l1);
      mv.visitLocalVariable(
          "this",
          "Lcom/android/tools/r8/debuginfo/InliningWithoutPositionsTestSource;",
          null,
          l0,
          l1,
          0);
      mv.visitMaxs(1, 1);
      mv.visitEnd();
    }
    if (throwLocation == Location.BAR) {
      mv = cw.visitMethod(ACC_PRIVATE + ACC_STATIC, "bar", "()V", null, null);
      mv.visitCode();
      Label l0 = new Label();
      mv.visitLabel(l0);
      if (barPos) {
        mv.visitLineNumber(Location.BAR.line, l0);
      }
      mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
      mv.visitInsn(DUP);
      mv.visitLdcInsn("<BAR-exception>");
      mv.visitMethodInsn(
          INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false);
      mv.visitInsn(ATHROW);
      mv.visitMaxs(3, 0);
      mv.visitEnd();
    } else {
      mv = cw.visitMethod(ACC_PRIVATE + ACC_STATIC, "bar", "()V", null, null);
      mv.visitCode();
      Label l0 = new Label();
      mv.visitLabel(l0);
      if (barPos) {
        mv.visitLineNumber(Location.BAR.line, l0);
      }
      mv.visitFieldInsn(GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;");
      mv.visitLdcInsn("<in-BAR>");
      mv.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
      Label l1 = new Label();
      mv.visitLabel(l1);
      mv.visitInsn(RETURN);
      mv.visitMaxs(2, 0);
      mv.visitEnd();
    }
    if (throwLocation == Location.FOO2) {
      mv = cw.visitMethod(ACC_PRIVATE + ACC_STATIC, "foo", "()V", null, null);
      mv.visitCode();
      Label l0 = new Label();
      mv.visitLabel(l0);
      if (foo1Pos) {
        mv.visitLineNumber(Location.FOO1.line, l0);
      }
      mv.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/debuginfo/InliningWithoutPositionsTestSource",
          "bar",
          "()V",
          false);
      Label l1 = new Label();
      mv.visitLabel(l1);
      if (foo2Pos) {
        mv.visitLineNumber(Location.FOO2.line, l1);
      }
      mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
      mv.visitInsn(DUP);
      mv.visitLdcInsn("<FOO2-exception>");
      mv.visitMethodInsn(
          INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false);
      mv.visitInsn(ATHROW);
      mv.visitMaxs(3, 0);
      mv.visitEnd();
    } else {
      mv = cw.visitMethod(ACC_PRIVATE + ACC_STATIC, "foo", "(Z)V", null, null);
      mv.visitCode();
      Label l0 = new Label();
      mv.visitLabel(l0);
      if (foo1Pos) {
        mv.visitLineNumber(Location.FOO1.line, l0);
      }
      mv.visitVarInsn(ILOAD, 0);
      Label l1 = new Label();
      mv.visitJumpInsn(IFEQ, l1);
      mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
      mv.visitInsn(DUP);
      mv.visitLdcInsn("<FOO1-exception>");
      mv.visitMethodInsn(
          INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false);
      mv.visitInsn(ATHROW);
      mv.visitLabel(l1);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/debuginfo/InliningWithoutPositionsTestSource",
          "bar",
          "()V",
          false);
      Label l2 = new Label();
      mv.visitLabel(l2);
      if (foo2Pos) {
        mv.visitLineNumber(Location.FOO2.line, l2);
      }
      mv.visitInsn(RETURN);
      Label l3 = new Label();
      mv.visitLabel(l3);
      mv.visitLocalVariable("b", "Z", null, l0, l3, 0);
      mv.visitMaxs(3, 1);
      mv.visitEnd();
    }

    {
      mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      mv.visitCode();
      Label l0 = new Label();
      mv.visitLabel(l0);
      if (mainPos) {
        mv.visitLineNumber(Location.MAIN.line, l0);
      }
      if (throwLocation == Location.FOO2) {
        mv.visitMethodInsn(
            INVOKESTATIC,
            "com/android/tools/r8/debuginfo/InliningWithoutPositionsTestSource",
            "foo",
            "()V",
            false);
      } else {
        mv.visitInsn(throwLocation == Location.FOO1 ? ICONST_1 : ICONST_0);
        mv.visitMethodInsn(
            INVOKESTATIC,
            "com/android/tools/r8/debuginfo/InliningWithoutPositionsTestSource",
            "foo",
            "(Z)V",
            false);
      }
      Label l1 = new Label();
      mv.visitLabel(l1);
      mv.visitInsn(RETURN);
      Label l2 = new Label();
      mv.visitLabel(l2);
      mv.visitLocalVariable("args", "[Ljava/lang/String;", null, l0, l2, 0);
      if (throwLocation == Location.FOO2) {
        mv.visitMaxs(0, 1);
      } else {
        mv.visitMaxs(1, 1);
      }
      mv.visitEnd();
    }

    cw.visitEnd();

    return cw.toByteArray();
  }
}
