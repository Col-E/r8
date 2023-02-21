// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.switches;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class SwitchMapCollectionWithoutStaticGetTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SwitchMapCollectionWithoutStaticGetTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, MyEnum.class)
        .addProgramClassFileData(SwitchMapClassDump.dump())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class TestClass {

    public static void main(String[] args) {
      MyEnum x = System.currentTimeMillis() >= 0 ? MyEnum.A : MyEnum.B;
      switch (x) {
        case A:
          System.out.println("Hello world!");
          break;

        case B:
          throw new RuntimeException();
      }
    }
  }

  enum MyEnum {
    A,
    B
  }

  /**
   * The following is the javac generated switch map for {@link MyEnum} with changes documented
   * below.
   */
  static class SwitchMapClassDump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      FieldVisitor fieldVisitor;
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_SUPER | ACC_SYNTHETIC,
          "com/android/tools/r8/ir/optimize/switches/SwitchMapCollectionWithoutStaticGetTest$1",
          null,
          "java/lang/Object",
          null);

      classWriter.visitSource("SwitchMapCollectionWithoutStaticGetTest.java", null);

      classWriter.visitOuterClass(
          "com/android/tools/r8/ir/optimize/switches/SwitchMapCollectionWithoutStaticGetTest",
          null,
          null);

      classWriter.visitInnerClass(
          "com/android/tools/r8/ir/optimize/switches/SwitchMapCollectionWithoutStaticGetTest$1",
          null,
          null,
          ACC_STATIC | ACC_SYNTHETIC);

      classWriter.visitInnerClass(
          "com/android/tools/r8/ir/optimize/switches/SwitchMapCollectionWithoutStaticGetTest$MyEnum",
          "com/android/tools/r8/ir/optimize/switches/SwitchMapCollectionWithoutStaticGetTest",
          "MyEnum",
          ACC_FINAL | ACC_STATIC | ACC_ENUM);

      {
        fieldVisitor =
            classWriter.visitField(
                ACC_FINAL | ACC_STATIC | ACC_SYNTHETIC,
                "$SwitchMap$com$android$tools$r8$ir$optimize$switches$SwitchMapCollectionWithoutStaticGetTest$MyEnum",
                "[I",
                null,
                null);
        fieldVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        Label label1 = new Label();
        Label label2 = new Label();
        methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/NoSuchFieldError");
        Label label3 = new Label();
        Label label4 = new Label();
        Label label5 = new Label();
        methodVisitor.visitTryCatchBlock(label3, label4, label5, "java/lang/NoSuchFieldError");
        Label label6 = new Label();
        methodVisitor.visitLabel(label6);
        methodVisitor.visitLineNumber(52, label6);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "com/android/tools/r8/ir/optimize/switches/SwitchMapCollectionWithoutStaticGetTest$MyEnum",
            "values",
            "()[Lcom/android/tools/r8/ir/optimize/switches/SwitchMapCollectionWithoutStaticGetTest$MyEnum;",
            false);
        methodVisitor.visitInsn(ARRAYLENGTH);
        methodVisitor.visitIntInsn(NEWARRAY, T_INT);
        methodVisitor.visitInsn(DUP); // NEW: DUP instead of GETSTATIC below.
        methodVisitor.visitFieldInsn(
            PUTSTATIC,
            "com/android/tools/r8/ir/optimize/switches/SwitchMapCollectionWithoutStaticGetTest$1",
            "$SwitchMap$com$android$tools$r8$ir$optimize$switches$SwitchMapCollectionWithoutStaticGetTest$MyEnum",
            "[I");
        methodVisitor.visitLabel(label0);
        // methodVisitor.visitFieldInsn(GETSTATIC,
        // "com/android/tools/r8/ir/optimize/switches/SwitchMapCollectionWithoutStaticGetTest$1",
        // "$SwitchMap$com$android$tools$r8$ir$optimize$switches$SwitchMapCollectionWithoutStaticGetTest$MyEnum", "[I");
        methodVisitor.visitFieldInsn(
            GETSTATIC,
            "com/android/tools/r8/ir/optimize/switches/SwitchMapCollectionWithoutStaticGetTest$MyEnum",
            "A",
            "Lcom/android/tools/r8/ir/optimize/switches/SwitchMapCollectionWithoutStaticGetTest$MyEnum;");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/android/tools/r8/ir/optimize/switches/SwitchMapCollectionWithoutStaticGetTest$MyEnum",
            "ordinal",
            "()I",
            false);
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitInsn(IASTORE);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitJumpInsn(GOTO, label3);
        methodVisitor.visitLabel(label2);
        methodVisitor.visitFrame(
            Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/NoSuchFieldError"});
        methodVisitor.visitVarInsn(ASTORE, 0);
        methodVisitor.visitLabel(label3);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitFieldInsn(
            GETSTATIC,
            "com/android/tools/r8/ir/optimize/switches/SwitchMapCollectionWithoutStaticGetTest$1",
            "$SwitchMap$com$android$tools$r8$ir$optimize$switches$SwitchMapCollectionWithoutStaticGetTest$MyEnum",
            "[I");
        methodVisitor.visitFieldInsn(
            GETSTATIC,
            "com/android/tools/r8/ir/optimize/switches/SwitchMapCollectionWithoutStaticGetTest$MyEnum",
            "B",
            "Lcom/android/tools/r8/ir/optimize/switches/SwitchMapCollectionWithoutStaticGetTest$MyEnum;");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/android/tools/r8/ir/optimize/switches/SwitchMapCollectionWithoutStaticGetTest$MyEnum",
            "ordinal",
            "()I",
            false);
        methodVisitor.visitInsn(ICONST_2);
        methodVisitor.visitInsn(IASTORE);
        methodVisitor.visitLabel(label4);
        Label label7 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label7);
        methodVisitor.visitLabel(label5);
        methodVisitor.visitFrame(
            Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/NoSuchFieldError"});
        methodVisitor.visitVarInsn(ASTORE, 0);
        methodVisitor.visitLabel(label7);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(3, 1);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
