// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class DesugarToClassFileB156591935 extends TestBase implements Opcodes {

  @Parameterized.Parameters(name = "{0}")
  public static AndroidApiLevel[] data() {
    return AndroidApiLevel.values();
  }

  private final AndroidApiLevel apiLevel;

  public DesugarToClassFileB156591935(AndroidApiLevel apiLevel) {
    this.apiLevel = apiLevel;
  }

  private void expectNops(CodeInspector inspector, int numberOfNops) {
    ClassSubject a = inspector.clazz("A");
    assertEquals(
        numberOfNops, a.clinit().streamInstructions().filter(InstructionSubject::isNop).count());
  }

  @Test
  public void test() throws Exception {
    // No nops in the input - see dump below.
    // TODO(b/156591935): The three nops should be avoided.
    testForD8(Backend.CF)
        .addProgramClassFileData(dump())
        .setMinApi(apiLevel)
        .compile()
        .inspect(inspector -> expectNops(inspector, 3));
  }

  /*
    Dump of the compiled code for ths class below. The dump has not been modified, but the
    code needs to have the specific line breaks for javac to insert the line numbers in the
    way that this test is about. Used a dump to avoid source formatting invalidating the test.

    static class A {
      // The line break before createA is needed for the expected line info.
      public static final A A_1 =
          createA(1, "FIRST");
      public static final A A_2 =
          createA(1, "SECOND");
      public static final A A_3 =
          createA(1, "THIRD");

      private final int value;
      private final String name;

      private static A createA(int value, String name) {
        return new A(value, name);
      }

      private A(int value, String name) {
        this.value = value;
        this.name = name;
      }

      int getValue() {
        return value;
      }

      String getName() {
        return name;
      }
    }
  */
  public static byte[] dump() {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;

    classWriter.visit(V1_8, ACC_SUPER, "A", null, "java/lang/Object", null);

    classWriter.visitSource("A.java", null);

    {
      fieldVisitor =
          classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "A_1", "LA;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "A_2", "LA;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "A_3", "LA;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor = classWriter.visitField(ACC_PRIVATE | ACC_FINAL, "value", "I", null, null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(ACC_PRIVATE | ACC_FINAL, "name", "Ljava/lang/String;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PRIVATE | ACC_STATIC, "createA", "(ILjava/lang/String;)LA;", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(69, label0);
      methodVisitor.visitTypeInsn(NEW, "A");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ILOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "A", "<init>", "(ILjava/lang/String;)V", false);
      methodVisitor.visitInsn(ARETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable("value", "I", null, label0, label1, 0);
      methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label0, label1, 1);
      methodVisitor.visitMaxs(4, 2);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PRIVATE, "<init>", "(ILjava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(72, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(73, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitFieldInsn(PUTFIELD, "A", "value", "I");
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(74, label2);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 2);
      methodVisitor.visitFieldInsn(PUTFIELD, "A", "name", "Ljava/lang/String;");
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(75, label3);
      methodVisitor.visitInsn(RETURN);
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLocalVariable("this", "LA;", null, label0, label4, 0);
      methodVisitor.visitLocalVariable("value", "I", null, label0, label4, 1);
      methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label0, label4, 2);
      methodVisitor.visitMaxs(2, 3);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "getValue", "()I", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(78, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(GETFIELD, "A", "value", "I");
      methodVisitor.visitInsn(IRETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable("this", "LA;", null, label0, label1, 0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "getName", "()Ljava/lang/String;", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(82, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(GETFIELD, "A", "name", "Ljava/lang/String;");
      methodVisitor.visitInsn(ARETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable("this", "LA;", null, label0, label1, 0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(67, label0);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitLdcInsn("FIRST");
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(68, label1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "A", "createA", "(ILjava/lang/String;)LA;", false);
      methodVisitor.visitFieldInsn(PUTSTATIC, "A", "A_1", "LA;");
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(69, label2);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitLdcInsn("SECOND");
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(70, label3);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "A", "createA", "(ILjava/lang/String;)LA;", false);
      methodVisitor.visitFieldInsn(PUTSTATIC, "A", "A_2", "LA;");
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(71, label4);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitLdcInsn("THIRD");
      Label label5 = new Label();
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(72, label5);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "A", "createA", "(ILjava/lang/String;)LA;", false);
      methodVisitor.visitFieldInsn(PUTSTATIC, "A", "A_3", "LA;");
      Label label6 = new Label();
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(71, label6);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 0);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
