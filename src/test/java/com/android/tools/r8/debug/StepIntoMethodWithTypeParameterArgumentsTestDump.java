// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class StepIntoMethodWithTypeParameterArgumentsTestDump implements Opcodes {

  public static byte[] dump(boolean moveFirstLineEntry) {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;

    classWriter.visit(
        V1_8,
        ACC_PUBLIC | ACC_SUPER,
        "com/android/tools/r8/debug/StepIntoMethodWithTypeParameterArgumentsTest",
        null,
        "java/lang/Object",
        null);

    classWriter.visitSource("StepIntoMethodWithTypeParameterArgumentsTest.java", null);

    {
      fieldVisitor =
          classWriter.visitField(
              ACC_PUBLIC | ACC_STATIC,
              "field",
              "Ljava/util/List;",
              "Ljava/util/List<Ljava/lang/Object;>;",
              null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(9, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable(
          "this",
          "Lcom/android/tools/r8/debug/StepIntoMethodWithTypeParameterArgumentsTest;",
          null,
          label0,
          label1,
          0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC,
              "foo",
              "(Ljava/util/List;)V",
              "(Ljava/util/List<Ljava/lang/String;>;)V",
              null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      if (!moveFirstLineEntry) {
        // Removed to have 'strings' start at undefined line number.
        methodVisitor.visitLineNumber(14, label0);
      }
      methodVisitor.visitFieldInsn(
          GETSTATIC,
          "com/android/tools/r8/debug/StepIntoMethodWithTypeParameterArgumentsTest",
          "field",
          "Ljava/util/List;");
      methodVisitor.visitVarInsn(ASTORE, 1);
      if (moveFirstLineEntry) {
        // Move the line entry to after 'strings' has already become live.
        Label labelNop = new Label();
        methodVisitor.visitLabel(labelNop);
        methodVisitor.visitLineNumber(14, labelNop);
        methodVisitor.visitInsn(NOP);
      }
      // Now at line 13 objects will be live.
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(15, label1);
      methodVisitor.visitInsn(RETURN);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLocalVariable(
          "strings", "Ljava/util/List;", "Ljava/util/List<Ljava/lang/String;>;", label0, label2, 0);
      methodVisitor.visitLocalVariable(
          "objects",
          "Ljava/util/Collection;",
          "Ljava/util/Collection<Ljava/lang/Object;>;",
          label1,
          label2,
          1);
      methodVisitor.visitMaxs(1, 2);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(18, label0);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "com/android/tools/r8/debug/StepIntoMethodWithTypeParameterArgumentsTest",
          "foo",
          "(Ljava/util/List;)V",
          false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(19, label1);
      methodVisitor.visitInsn(RETURN);
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLocalVariable("args", "[Ljava/lang/String;", null, label0, label2, 0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(11, label0);
      methodVisitor.visitInsn(ACONST_NULL);
      methodVisitor.visitFieldInsn(
          PUTSTATIC,
          "com/android/tools/r8/debug/StepIntoMethodWithTypeParameterArgumentsTest",
          "field",
          "Ljava/util/List;");
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 0);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
