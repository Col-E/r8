// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir;

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
import org.objectweb.asm.Type;

@RunWith(Parameterized.class)
public class DebugLocalStartRangeInLinearBlockWithFrameTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DebugLocalStartRangeInLinearBlockWithFrameTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(AbstractAjaxCallbackDump.dump())
        .setMinApi(parameters)
        .compile();
  }

  public static class AbstractAjaxCallbackDump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      FieldVisitor fieldVisitor;
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_6,
          ACC_PUBLIC | ACC_SUPER | ACC_ABSTRACT,
          "com/androidquery/callback/AbstractAjaxCallback",
          "<T:Ljava/lang/Object;K:Ljava/lang/Object;>Ljava/lang/Object;Ljava/lang/Runnable;",
          "java/lang/Object",
          new String[] {"java/lang/Runnable"});

      classWriter.visitSource("AbstractAjaxCallback.java", null);

      classWriter.visitInnerClass(
          "android/os/Build$VERSION", "android/os/Build", "VERSION", ACC_PUBLIC | ACC_STATIC);

      classWriter.visitInnerClass(
          "com/androidquery/callback/AbstractAjaxCallback$1", null, null, 0);

      classWriter.visitInnerClass(
          "java/net/Proxy$Type",
          "java/net/Proxy",
          "Type",
          ACC_PUBLIC | ACC_FINAL | ACC_STATIC | ACC_ENUM);

      classWriter.visitInnerClass(
          "java/util/Map$Entry",
          "java/util/Map",
          "Entry",
          ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_INTERFACE);

      {
        fieldVisitor =
            classWriter.visitField(
                ACC_PRIVATE, "type", "Ljava/lang/Class;", "Ljava/lang/Class<TT;>;", null);
        fieldVisitor.visitEnd();
      }
      {
        fieldVisitor =
            classWriter.visitField(ACC_PRIVATE, "handler", "Ljava/lang/Object;", null, null);
        fieldVisitor.visitEnd();
      }
      {
        fieldVisitor =
            classWriter.visitField(ACC_PRIVATE, "callback", "Ljava/lang/String;", null, null);
        fieldVisitor.visitEnd();
      }
      {
        fieldVisitor = classWriter.visitField(ACC_PRIVATE, "url", "Ljava/lang/String;", null, null);
        fieldVisitor.visitEnd();
      }
      {
        fieldVisitor =
            classWriter.visitField(ACC_PROTECTED, "result", "Ljava/lang/Object;", "TT;", null);
        fieldVisitor.visitEnd();
      }
      {
        fieldVisitor =
            classWriter.visitField(
                ACC_PROTECTED, "status", "Lcom/androidquery/callback/AjaxStatus;", null, null);
        fieldVisitor.visitEnd();
      }
      {
        fieldVisitor = classWriter.visitField(ACC_PRIVATE, "completed", "Z", null, null);
        fieldVisitor.visitEnd();
      }
      {
        fieldVisitor = classWriter.visitField(ACC_PRIVATE, "blocked", "Z", null, null);
        fieldVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(0, "callback", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        Label label1 = new Label();
        Label label2 = new Label();
        methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Exception");
        Label label3 = new Label();
        methodVisitor.visitLabel(label3);
        methodVisitor.visitLineNumber(568, label3);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/androidquery/callback/AbstractAjaxCallback",
            "showProgress",
            "(Z)V",
            false);
        Label label4 = new Label();
        methodVisitor.visitLabel(label4);
        methodVisitor.visitLineNumber(570, label4);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitFieldInsn(
            PUTFIELD, "com/androidquery/callback/AbstractAjaxCallback", "completed", "Z");
        Label label5 = new Label();
        methodVisitor.visitLabel(label5);
        methodVisitor.visitLineNumber(572, label5);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/androidquery/callback/AbstractAjaxCallback",
            "isActive",
            "()Z",
            false);
        Label label6 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label6);
        Label label7 = new Label();
        methodVisitor.visitLabel(label7);
        methodVisitor.visitLineNumber(574, label7);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "com/androidquery/callback/AbstractAjaxCallback",
            "callback",
            "Ljava/lang/String;");
        methodVisitor.visitJumpInsn(IFNULL, label0);
        Label label8 = new Label();
        methodVisitor.visitLabel(label8);
        methodVisitor.visitLineNumber(575, label8);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/androidquery/callback/AbstractAjaxCallback",
            "getHandler",
            "()Ljava/lang/Object;",
            false);
        methodVisitor.visitVarInsn(ASTORE, 1);
        Label label9 = new Label();
        methodVisitor.visitLabel(label9);
        methodVisitor.visitLineNumber(576, label9);
        methodVisitor.visitInsn(ICONST_3);
        methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/Class");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitLdcInsn(Type.getType("Ljava/lang/String;"));
        methodVisitor.visitInsn(AASTORE);
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "com/androidquery/callback/AbstractAjaxCallback",
            "type",
            "Ljava/lang/Class;");
        methodVisitor.visitInsn(AASTORE);
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitInsn(ICONST_2);
        methodVisitor.visitLdcInsn(Type.getType("Lcom/androidquery/callback/AjaxStatus;"));
        methodVisitor.visitInsn(AASTORE);
        methodVisitor.visitVarInsn(ASTORE, 2);
        Label label10 = new Label();
        methodVisitor.visitLabel(label10);
        methodVisitor.visitLineNumber(577, label10);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "com/androidquery/callback/AbstractAjaxCallback",
            "callback",
            "Ljava/lang/String;");
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitFieldInsn(
            GETSTATIC,
            "com/androidquery/callback/AbstractAjaxCallback",
            "DEFAULT_SIG",
            "[Ljava/lang/Class;");
        methodVisitor.visitInsn(ICONST_3);
        methodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "com/androidquery/callback/AbstractAjaxCallback",
            "url",
            "Ljava/lang/String;");
        methodVisitor.visitInsn(AASTORE);
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "com/androidquery/callback/AbstractAjaxCallback",
            "result",
            "Ljava/lang/Object;");
        methodVisitor.visitInsn(AASTORE);
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitInsn(ICONST_2);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "com/androidquery/callback/AbstractAjaxCallback",
            "status",
            "Lcom/androidquery/callback/AjaxStatus;");
        methodVisitor.visitInsn(AASTORE);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "com/androidquery/util/AQUtility",
            "invokeHandler",
            "(Ljava/lang/Object;Ljava/lang/String;ZZ[Ljava/lang/Class;[Ljava/lang/Class;[Ljava/lang/Object;)Ljava/lang/Object;",
            false);
        methodVisitor.visitInsn(POP);
        Label label11 = new Label();
        methodVisitor.visitLabel(label11);
        methodVisitor.visitLineNumber(578, label11);
        Label label12 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label12);
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(580, label0);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "com/androidquery/callback/AbstractAjaxCallback",
            "url",
            "Ljava/lang/String;");
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "com/androidquery/callback/AbstractAjaxCallback",
            "result",
            "Ljava/lang/Object;");
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "com/androidquery/callback/AbstractAjaxCallback",
            "status",
            "Lcom/androidquery/callback/AjaxStatus;");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/androidquery/callback/AbstractAjaxCallback",
            "callback",
            "(Ljava/lang/String;Ljava/lang/Object;Lcom/androidquery/callback/AjaxStatus;)V",
            false);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(581, label1);
        methodVisitor.visitJumpInsn(GOTO, label12);
        methodVisitor.visitLabel(label2);
        methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Exception"});
        methodVisitor.visitVarInsn(ASTORE, 1);
        Label label13 = new Label();
        methodVisitor.visitLabel(label13);
        methodVisitor.visitLineNumber(582, label13);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "com/androidquery/util/AQUtility",
            "report",
            "(Ljava/lang/Throwable;)V",
            false);
        Label label14 = new Label();
        methodVisitor.visitLabel(label14);
        methodVisitor.visitLineNumber(586, label14);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitJumpInsn(GOTO, label12);
        methodVisitor.visitLabel(label6);
        methodVisitor.visitLineNumber(587, label6);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "com/androidquery/callback/AbstractAjaxCallback",
            "url",
            "Ljava/lang/String;");
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "com/androidquery/callback/AbstractAjaxCallback",
            "result",
            "Ljava/lang/Object;");
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "com/androidquery/callback/AbstractAjaxCallback",
            "status",
            "Lcom/androidquery/callback/AjaxStatus;");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/androidquery/callback/AbstractAjaxCallback",
            "skip",
            "(Ljava/lang/String;Ljava/lang/Object;Lcom/androidquery/callback/AjaxStatus;)V",
            false);
        methodVisitor.visitLabel(label12);
        methodVisitor.visitLineNumber(591, label12);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/androidquery/callback/AbstractAjaxCallback",
            "filePut",
            "()V",
            false);
        Label label15 = new Label();
        methodVisitor.visitLabel(label15);
        methodVisitor.visitLineNumber(593, label15);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD, "com/androidquery/callback/AbstractAjaxCallback", "blocked", "Z");
        Label label16 = new Label();
        methodVisitor.visitJumpInsn(IFNE, label16);
        Label label17 = new Label();
        methodVisitor.visitLabel(label17);
        methodVisitor.visitLineNumber(594, label17);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "com/androidquery/callback/AbstractAjaxCallback",
            "status",
            "Lcom/androidquery/callback/AjaxStatus;");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "com/androidquery/callback/AjaxStatus", "close", "()V", false);
        methodVisitor.visitLabel(label16);
        methodVisitor.visitLineNumber(597, label16);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL, "com/androidquery/callback/AbstractAjaxCallback", "wake", "()V", false);
        Label label18 = new Label();
        methodVisitor.visitLabel(label18);
        methodVisitor.visitLineNumber(598, label18);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC, "com/androidquery/util/AQUtility", "debugNotify", "()V", false);
        Label label19 = new Label();
        methodVisitor.visitLabel(label19);
        methodVisitor.visitLineNumber(599, label19);
        methodVisitor.visitInsn(RETURN);
        Label label20 = new Label();
        methodVisitor.visitLabel(label20);
        methodVisitor.visitLocalVariable(
            "this",
            "Lcom/androidquery/callback/AbstractAjaxCallback;",
            "Lcom/androidquery/callback/AbstractAjaxCallback<TT;TK;>;",
            label3,
            label20,
            0);
        methodVisitor.visitLocalVariable("handler", "Ljava/lang/Object;", null, label9, label11, 1);
        methodVisitor.visitLocalVariable(
            "AJAX_SIG", "[Ljava/lang/Class;", null, label10, label11, 2);
        methodVisitor.visitLocalVariable("e", "Ljava/lang/Exception;", null, label13, label14, 1);
        methodVisitor.visitMaxs(10, 3);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
