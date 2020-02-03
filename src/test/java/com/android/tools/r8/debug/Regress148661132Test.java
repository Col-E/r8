// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class Regress148661132Test extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public Regress148661132Test(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForD8().setMinApi(AndroidApiLevel.B).addProgramClassFileData(FlafKtDump.dump()).compile();
  }

  static class FlafKtDump implements Opcodes {

    public static byte[] dump() {

      ClassWriter cw = new ClassWriter(0);
      cw.visit(V1_6, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, "FlafKt", null, "java/lang/Object", null);
      cw.visitSource("Flaf.kt", null);
      cw.visitInnerClass("FlafKt$inlineFun$1", null, null, ACC_PUBLIC | ACC_FINAL | ACC_STATIC);

      MethodVisitor mv =
          cw.visitMethod(
              ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC,
              "inlineFun$default",
              "(LA;Lkotlin/jvm/functions/Function0;ILjava/lang/Object;)LA;",
              null,
              null);
      mv.visitCode();
      mv.visitVarInsn(ILOAD, 2);
      mv.visitInsn(ICONST_2);
      mv.visitInsn(IAND);
      Label label0 = new Label();
      mv.visitJumpInsn(IFEQ, label0);
      mv.visitTypeInsn(NEW, "FlafKt$inlineFun$1");
      mv.visitInsn(DUP);
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "FlafKt$inlineFun$1", "<init>", "(LA;)V", false);
      mv.visitTypeInsn(CHECKCAST, "kotlin/jvm/functions/Function0");
      mv.visitVarInsn(ASTORE, 1);
      mv.visitLabel(label0);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
      mv.visitVarInsn(ALOAD, 1);
      mv.visitVarInsn(ASTORE, 4);
      Label label1 = new Label();
      // The delayed introduction of the local variable causes a write after the phi.
      // That write should not be replaced as it invalidates the assumption of local info
      // associated with phis.
      mv.visitLabel(label1);
      mv.visitInsn(ICONST_0);
      mv.visitVarInsn(ISTORE, 5);
      Label label2 = new Label();
      mv.visitLabel(label2);
      mv.visitLineNumber(13, label2);
      mv.visitVarInsn(ALOAD, 4);
      mv.visitMethodInsn(
          INVOKEINTERFACE,
          "kotlin/jvm/functions/Function0",
          "invoke",
          "()Ljava/lang/Object;",
          true);
      mv.visitTypeInsn(CHECKCAST, "A");
      Label label3 = new Label();
      mv.visitLabel(label3);
      mv.visitInsn(ARETURN);
      mv.visitLocalVariable("$i$f$inlineFun", "I", null, label2, label3, 5);
      mv.visitLocalVariable(
          "lambda$iv", "Lkotlin/jvm/functions/Function0;", null, label1, label3, 4);
      mv.visitMaxs(3, 6);
      mv.visitEnd();

      cw.visitEnd();
      return cw.toByteArray();
    }
  }
}
