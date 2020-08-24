// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersBuilder;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
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
  public static TestParametersCollection data() {
    return TestParametersBuilder.builder().withCfRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private final AndroidApiLevel apiLevel;

  public DesugarToClassFileB156591935(TestParameters parameters) {
    this.apiLevel = parameters.getApiLevel();
  }

  private void expectNoNops(String className, CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(className);
    assertFalse(classSubject.clinit().streamInstructions().anyMatch(InstructionSubject::isNop));
  }

  private void expectNoLoad(String className, CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(className);
    assertFalse(
        classSubject
            .clinit()
            .streamInstructions()
            .anyMatch(instructionSubject -> instructionSubject.asCfInstruction().isLoad()));
  }

  private void expectNoStore(String className, CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(className);
    assertFalse(
        classSubject
            .clinit()
            .streamInstructions()
            .anyMatch(instructionSubject -> instructionSubject.asCfInstruction().isStore()));
  }

  private void expectNoSwap(String className, CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(className);
    assertFalse(
        classSubject
            .clinit()
            .streamInstructions()
            .anyMatch(
                instructionSubject ->
                    instructionSubject.asCfInstruction().isStackInstruction(Opcode.Swap)));
  }

  @Test
  public void test() throws Exception {
    // No nops in the input - see dump below.
    testForD8(Backend.CF)
        .addProgramClassFileData(dump())
        .setMinApi(apiLevel)
        .compile()
        .inspect(inspector -> expectNoNops("A", inspector))
        .inspect(inspector -> expectNoLoad("A", inspector))
        .inspect(inspector -> expectNoStore("A", inspector))
        .inspect(inspector -> expectNoSwap("A", inspector));
  }

  @Test
  public void testWithExtraLine() throws Exception {
    // No nops in the input - see dump below.
    testForD8(Backend.CF)
        .addProgramClassFileData(dumpWithExtraLine())
        .setMinApi(apiLevel)
        .compile()
        .inspect(inspector -> expectNoNops("A", inspector))
        .inspect(inspector -> expectNoLoad("A", inspector))
        .inspect(inspector -> expectNoStore("A", inspector))
        .inspect(inspector -> expectNoSwap("A", inspector));
  }

  @Test
  public void testWithMoreConsts() throws Exception {
    // No nops in the input - see dump below.
    testForD8(Backend.CF)
        .addProgramClassFileData(dumpMoreConsts())
        .setMinApi(apiLevel)
        .compile()
        .inspect(inspector -> expectNoNops("B", inspector))
        .inspect(inspector -> expectNoLoad("B", inspector))
        .inspect(inspector -> expectNoStore("B", inspector))
        .inspect(inspector -> expectNoSwap("B", inspector));
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
  private static byte[] dump() {

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

  // Patched version of the code above. Added an additional line change between the two const
  // instructions for the "createA" calls in <clinit>. Look for label variable names ending in x.
  private static byte[] dumpWithExtraLine() {

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
      Label label0x = new Label();
      methodVisitor.visitLabel(label0x);
      methodVisitor.visitLineNumber(68, label0x);
      methodVisitor.visitLdcInsn("FIRST");
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(69, label1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "A", "createA", "(ILjava/lang/String;)LA;", false);
      methodVisitor.visitFieldInsn(PUTSTATIC, "A", "A_1", "LA;");
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(70, label2);
      methodVisitor.visitInsn(ICONST_1);
      Label label2x = new Label();
      methodVisitor.visitLabel(label2x);
      methodVisitor.visitLineNumber(71, label2x);
      methodVisitor.visitLdcInsn("SECOND");
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(72, label3);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "A", "createA", "(ILjava/lang/String;)LA;", false);
      methodVisitor.visitFieldInsn(PUTSTATIC, "A", "A_2", "LA;");
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(73, label4);
      methodVisitor.visitInsn(ICONST_1);
      Label label4x = new Label();
      methodVisitor.visitLabel(label4x);
      methodVisitor.visitLineNumber(74, label4x);
      methodVisitor.visitLdcInsn("THIRD");
      Label label5 = new Label();
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(75, label5);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "A", "createA", "(ILjava/lang/String;)LA;", false);
      methodVisitor.visitFieldInsn(PUTSTATIC, "A", "A_3", "LA;");
      Label label6 = new Label();
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(74, label6);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 0);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  /*
    Dump of the compiled code for ths class below. The dump has not been modified, but the
    code needs to have the specific line breaks for javac to insert the line numbers in the
    way that this test is about. Used a dump to avoid source formatting invalidating the test.

    This is the same pattern like class A above, just with more constants attributed to the
    same line.

    class B {
      // The line break before createA is needed for the expected line info.
      public static final B B_1 =
          createB(1, 2, 3, "FIRST");
      public static final B B_2 =
          createB(1, 2, 3, "SECOND");
      public static final B B_3 =
          createB(1, 2, 3, "THIRD");

      private final int value1;
      private final int value2;
      private final int value3;
      private final String name;

      private static B createB(int value1, int value2, int value3, String name) {
        return new B(value1, value2, value3, name);
      }

      private B(int value1, int value2, int value3, String name) {
        this.value1 = value1;
        this.value2 = value2;
        this.value3 = value3;
        this.name = name;
      }

      int getValue1() {
        return value1;
      }

      int getVlaue2() {
        return value2;
      }

      int getValue3() {
        return value3;
      }

      String getName() {
        return name;
      }
    }
  */
  public static byte[] dumpMoreConsts() {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;

    classWriter.visit(V11, ACC_SUPER, "B", null, "java/lang/Object", null);

    classWriter.visitSource("B.java", null);

    {
      fieldVisitor =
          classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "B_1", "LB;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "B_2", "LB;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "B_3", "LB;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor = classWriter.visitField(ACC_PRIVATE | ACC_FINAL, "value1", "I", null, null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor = classWriter.visitField(ACC_PRIVATE | ACC_FINAL, "value2", "I", null, null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor = classWriter.visitField(ACC_PRIVATE | ACC_FINAL, "value3", "I", null, null);
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
              ACC_PRIVATE | ACC_STATIC, "createB", "(IIILjava/lang/String;)LB;", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(16, label0);
      methodVisitor.visitTypeInsn(NEW, "B");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitVarInsn(ILOAD, 0);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitVarInsn(ILOAD, 2);
      methodVisitor.visitVarInsn(ALOAD, 3);
      methodVisitor.visitMethodInsn(
          INVOKESPECIAL, "B", "<init>", "(IIILjava/lang/String;)V", false);
      methodVisitor.visitInsn(ARETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable("value1", "I", null, label0, label1, 0);
      methodVisitor.visitLocalVariable("value2", "I", null, label0, label1, 1);
      methodVisitor.visitLocalVariable("value3", "I", null, label0, label1, 2);
      methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label0, label1, 3);
      methodVisitor.visitMaxs(6, 4);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PRIVATE, "<init>", "(IIILjava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(19, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(20, label1);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitFieldInsn(PUTFIELD, "B", "value1", "I");
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(21, label2);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ILOAD, 2);
      methodVisitor.visitFieldInsn(PUTFIELD, "B", "value2", "I");
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(22, label3);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ILOAD, 3);
      methodVisitor.visitFieldInsn(PUTFIELD, "B", "value3", "I");
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(23, label4);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 4);
      methodVisitor.visitFieldInsn(PUTFIELD, "B", "name", "Ljava/lang/String;");
      Label label5 = new Label();
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(24, label5);
      methodVisitor.visitInsn(RETURN);
      Label label6 = new Label();
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLocalVariable("this", "LB;", null, label0, label6, 0);
      methodVisitor.visitLocalVariable("value1", "I", null, label0, label6, 1);
      methodVisitor.visitLocalVariable("value2", "I", null, label0, label6, 2);
      methodVisitor.visitLocalVariable("value3", "I", null, label0, label6, 3);
      methodVisitor.visitLocalVariable("name", "Ljava/lang/String;", null, label0, label6, 4);
      methodVisitor.visitMaxs(2, 5);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "getValue1", "()I", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(27, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(GETFIELD, "B", "value1", "I");
      methodVisitor.visitInsn(IRETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable("this", "LB;", null, label0, label1, 0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "getVlaue2", "()I", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(31, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(GETFIELD, "B", "value2", "I");
      methodVisitor.visitInsn(IRETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable("this", "LB;", null, label0, label1, 0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "getValue3", "()I", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(35, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(GETFIELD, "B", "value3", "I");
      methodVisitor.visitInsn(IRETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable("this", "LB;", null, label0, label1, 0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(0, "getName", "()Ljava/lang/String;", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(39, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(GETFIELD, "B", "name", "Ljava/lang/String;");
      methodVisitor.visitInsn(ARETURN);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLocalVariable("this", "LB;", null, label0, label1, 0);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(3, label0);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitInsn(ICONST_3);
      methodVisitor.visitLdcInsn("FIRST");
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(4, label1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "B", "createB", "(IIILjava/lang/String;)LB;", false);
      methodVisitor.visitFieldInsn(PUTSTATIC, "B", "B_1", "LB;");
      Label label2 = new Label();
      methodVisitor.visitLabel(label2);
      methodVisitor.visitLineNumber(5, label2);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitInsn(ICONST_3);
      methodVisitor.visitLdcInsn("SECOND");
      Label label3 = new Label();
      methodVisitor.visitLabel(label3);
      methodVisitor.visitLineNumber(6, label3);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "B", "createB", "(IIILjava/lang/String;)LB;", false);
      methodVisitor.visitFieldInsn(PUTSTATIC, "B", "B_2", "LB;");
      Label label4 = new Label();
      methodVisitor.visitLabel(label4);
      methodVisitor.visitLineNumber(7, label4);
      methodVisitor.visitInsn(ICONST_1);
      methodVisitor.visitInsn(ICONST_2);
      methodVisitor.visitInsn(ICONST_3);
      methodVisitor.visitLdcInsn("THIRD");
      Label label5 = new Label();
      methodVisitor.visitLabel(label5);
      methodVisitor.visitLineNumber(8, label5);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "B", "createB", "(IIILjava/lang/String;)LB;", false);
      methodVisitor.visitFieldInsn(PUTSTATIC, "B", "B_3", "LB;");
      Label label6 = new Label();
      methodVisitor.visitLabel(label6);
      methodVisitor.visitLineNumber(7, label6);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(4, 0);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
