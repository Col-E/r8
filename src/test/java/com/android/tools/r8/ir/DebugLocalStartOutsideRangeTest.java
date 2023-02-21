// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.ir.DebugLocalStartOutsideRangeTest.PrintHelper$PrintUriAdapter$1Dump.dump;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.CompilationFailedException;
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
public class DebugLocalStartOutsideRangeTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public DebugLocalStartOutsideRangeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws CompilationFailedException {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(dump())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertNoWarningsMatch(
                  diagnosticMessage(containsString("Could not find phi type for register")));
            });
  }

  public static class PrintHelper$PrintUriAdapter$1Dump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      FieldVisitor fieldVisitor;
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_7,
          ACC_SUPER,
          "androidx/print/PrintHelper$PrintUriAdapter$1",
          "Landroid/os/AsyncTask<Landroid/net/Uri;Ljava/lang/Boolean;Landroid/graphics/Bitmap;>;",
          "android/os/AsyncTask",
          null);

      classWriter.visitSource("PrintHelper.java", null);

      classWriter.visitOuterClass(
          "androidx/print/PrintHelper$PrintUriAdapter",
          "onLayout",
          "(Landroid/print/PrintAttributes;Landroid/print/PrintAttributes;Landroid/os/CancellationSignal;Landroid/print/PrintDocumentAdapter$LayoutResultCallback;Landroid/os/Bundle;)V");

      classWriter.visitInnerClass(
          "androidx/print/PrintHelper$PrintUriAdapter",
          "androidx/print/PrintHelper",
          "PrintUriAdapter",
          ACC_PRIVATE);

      classWriter.visitInnerClass("androidx/print/PrintHelper$PrintUriAdapter$1", null, null, 0);

      classWriter.visitInnerClass(
          "android/print/PrintAttributes$MediaSize",
          "android/print/PrintAttributes",
          "MediaSize",
          ACC_PUBLIC | ACC_FINAL | ACC_STATIC);

      classWriter.visitInnerClass(
          "android/print/PrintDocumentInfo$Builder",
          "android/print/PrintDocumentInfo",
          "Builder",
          ACC_PUBLIC | ACC_FINAL | ACC_STATIC);

      {
        fieldVisitor =
            classWriter.visitField(
                ACC_FINAL | ACC_SYNTHETIC,
                "val$cancellationSignal",
                "Landroid/os/CancellationSignal;",
                null,
                null);
        fieldVisitor.visitEnd();
      }
      {
        fieldVisitor =
            classWriter.visitField(
                ACC_FINAL | ACC_SYNTHETIC,
                "val$newPrintAttributes",
                "Landroid/print/PrintAttributes;",
                null,
                null);
        fieldVisitor.visitEnd();
      }
      {
        fieldVisitor =
            classWriter.visitField(
                ACC_FINAL | ACC_SYNTHETIC,
                "val$oldPrintAttributes",
                "Landroid/print/PrintAttributes;",
                null,
                null);
        fieldVisitor.visitEnd();
      }
      {
        fieldVisitor =
            classWriter.visitField(
                ACC_FINAL | ACC_SYNTHETIC,
                "val$layoutResultCallback",
                "Landroid/print/PrintDocumentAdapter$LayoutResultCallback;",
                null,
                null);
        fieldVisitor.visitEnd();
      }
      {
        fieldVisitor =
            classWriter.visitField(
                ACC_FINAL | ACC_SYNTHETIC,
                "this$1",
                "Landroidx/print/PrintHelper$PrintUriAdapter;",
                null,
                null);
        fieldVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PROTECTED, "onPostExecute", "(Landroid/graphics/Bitmap;)V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        Label label1 = new Label();
        Label label2 = new Label();
        methodVisitor.visitTryCatchBlock(label0, label1, label2, null);
        Label label3 = new Label();
        methodVisitor.visitTryCatchBlock(label2, label3, label2, null);
        Label label4 = new Label();
        methodVisitor.visitLabel(label4);
        methodVisitor.visitLineNumber(450, label4);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL, "android/os/AsyncTask", "onPostExecute", "(Ljava/lang/Object;)V", false);
        Label label5 = new Label();
        methodVisitor.visitLabel(label5);
        methodVisitor.visitLineNumber(454, label5);
        methodVisitor.visitVarInsn(ALOAD, 1);
        Label label6 = new Label();
        methodVisitor.visitJumpInsn(IFNULL, label6);
        methodVisitor.visitFieldInsn(
            GETSTATIC, "androidx/print/PrintHelper", "PRINT_ACTIVITY_RESPECTS_ORIENTATION", "Z");
        Label label7 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label7);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "androidx/print/PrintHelper$PrintUriAdapter$1",
            "this$1",
            "Landroidx/print/PrintHelper$PrintUriAdapter;");
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "androidx/print/PrintHelper$PrintUriAdapter",
            "this$0",
            "Landroidx/print/PrintHelper;");
        methodVisitor.visitFieldInsn(GETFIELD, "androidx/print/PrintHelper", "mOrientation", "I");
        methodVisitor.visitJumpInsn(IFNE, label6);
        methodVisitor.visitLabel(label7);
        methodVisitor.visitLineNumber(458, label7);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitVarInsn(ASTORE, 3);
        methodVisitor.visitInsn(MONITORENTER);
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(459, label0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "androidx/print/PrintHelper$PrintUriAdapter$1",
            "this$1",
            "Landroidx/print/PrintHelper$PrintUriAdapter;");
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "androidx/print/PrintHelper$PrintUriAdapter",
            "mAttributes",
            "Landroid/print/PrintAttributes;");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "android/print/PrintAttributes",
            "getMediaSize",
            "()Landroid/print/PrintAttributes$MediaSize;",
            false);
        methodVisitor.visitVarInsn(ASTORE, 2);
        Label label8 = new Label();
        methodVisitor.visitLabel(label8);
        methodVisitor.visitLineNumber(460, label8);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitInsn(MONITOREXIT);
        methodVisitor.visitLabel(label1);
        Label label9 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label9);
        methodVisitor.visitLabel(label2);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            4,
            new Object[] {
              "androidx/print/PrintHelper$PrintUriAdapter$1",
              "android/graphics/Bitmap",
              Opcodes.TOP,
              "java/lang/Object"
            },
            1,
            new Object[] {"java/lang/Throwable"});
        methodVisitor.visitVarInsn(ASTORE, 4);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitInsn(MONITOREXIT);
        methodVisitor.visitLabel(label3);
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitInsn(ATHROW);
        methodVisitor.visitLabel(label9);
        methodVisitor.visitLineNumber(462, label9);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            3,
            new Object[] {
              "androidx/print/PrintHelper$PrintUriAdapter$1",
              "android/graphics/Bitmap",
              "android/print/PrintAttributes$MediaSize"
            },
            0,
            new Object[] {});
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitJumpInsn(IFNULL, label6);
        Label label10 = new Label();
        methodVisitor.visitLabel(label10);
        methodVisitor.visitLineNumber(463, label10);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "android/print/PrintAttributes$MediaSize", "isPortrait", "()Z", false);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "androidx/print/PrintHelper",
            "isPortrait",
            "(Landroid/graphics/Bitmap;)Z",
            false);
        methodVisitor.visitJumpInsn(IF_ICMPEQ, label6);
        Label label11 = new Label();
        methodVisitor.visitLabel(label11);
        methodVisitor.visitLineNumber(464, label11);
        methodVisitor.visitTypeInsn(NEW, "android/graphics/Matrix");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL, "android/graphics/Matrix", "<init>", "()V", false);
        methodVisitor.visitVarInsn(ASTORE, 3);
        Label label12 = new Label();
        methodVisitor.visitLabel(label12);
        methodVisitor.visitLineNumber(466, label12);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitLdcInsn(new Float("90.0"));
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "android/graphics/Matrix", "postRotate", "(F)Z", false);
        methodVisitor.visitInsn(POP);
        Label label13 = new Label();
        methodVisitor.visitLabel(label13);
        methodVisitor.visitLineNumber(467, label13);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ALOAD, 1);
        Label label14 = new Label();
        methodVisitor.visitLabel(label14);
        methodVisitor.visitLineNumber(468, label14);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "android/graphics/Bitmap", "getWidth", "()I", false);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "android/graphics/Bitmap", "getHeight", "()I", false);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitInsn(ICONST_1);
        Label label15 = new Label();
        methodVisitor.visitLabel(label15);
        methodVisitor.visitLineNumber(467, label15);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "android/graphics/Bitmap",
            "createBitmap",
            "(Landroid/graphics/Bitmap;IIIILandroid/graphics/Matrix;Z)Landroid/graphics/Bitmap;",
            false);
        methodVisitor.visitVarInsn(ASTORE, 1);
        methodVisitor.visitLabel(label6);
        methodVisitor.visitLineNumber(474, label6);
        methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "androidx/print/PrintHelper$PrintUriAdapter$1",
            "this$1",
            "Landroidx/print/PrintHelper$PrintUriAdapter;");
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitFieldInsn(
            PUTFIELD,
            "androidx/print/PrintHelper$PrintUriAdapter",
            "mBitmap",
            "Landroid/graphics/Bitmap;");
        Label label16 = new Label();
        methodVisitor.visitLabel(label16);
        methodVisitor.visitLineNumber(475, label16);
        methodVisitor.visitVarInsn(ALOAD, 1);
        Label label17 = new Label();
        methodVisitor.visitJumpInsn(IFNULL, label17);
        Label label18 = new Label();
        methodVisitor.visitLabel(label18);
        methodVisitor.visitLineNumber(476, label18);
        methodVisitor.visitTypeInsn(NEW, "android/print/PrintDocumentInfo$Builder");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "androidx/print/PrintHelper$PrintUriAdapter$1",
            "this$1",
            "Landroidx/print/PrintHelper$PrintUriAdapter;");
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "androidx/print/PrintHelper$PrintUriAdapter",
            "mJobName",
            "Ljava/lang/String;");
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "android/print/PrintDocumentInfo$Builder",
            "<init>",
            "(Ljava/lang/String;)V",
            false);
        methodVisitor.visitInsn(ICONST_1);
        Label label19 = new Label();
        methodVisitor.visitLabel(label19);
        methodVisitor.visitLineNumber(477, label19);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "android/print/PrintDocumentInfo$Builder",
            "setContentType",
            "(I)Landroid/print/PrintDocumentInfo$Builder;",
            false);
        methodVisitor.visitInsn(ICONST_1);
        Label label20 = new Label();
        methodVisitor.visitLabel(label20);
        methodVisitor.visitLineNumber(478, label20);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "android/print/PrintDocumentInfo$Builder",
            "setPageCount",
            "(I)Landroid/print/PrintDocumentInfo$Builder;",
            false);
        Label label21 = new Label();
        methodVisitor.visitLabel(label21);
        methodVisitor.visitLineNumber(479, label21);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "android/print/PrintDocumentInfo$Builder",
            "build",
            "()Landroid/print/PrintDocumentInfo;",
            false);
        methodVisitor.visitVarInsn(ASTORE, 2);
        Label label22 = new Label();
        methodVisitor.visitLabel(label22);
        methodVisitor.visitLineNumber(481, label22);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "androidx/print/PrintHelper$PrintUriAdapter$1",
            "val$newPrintAttributes",
            "Landroid/print/PrintAttributes;");
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "androidx/print/PrintHelper$PrintUriAdapter$1",
            "val$oldPrintAttributes",
            "Landroid/print/PrintAttributes;");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "android/print/PrintAttributes",
            "equals",
            "(Ljava/lang/Object;)Z",
            false);
        Label label23 = new Label();
        methodVisitor.visitJumpInsn(IFNE, label23);
        methodVisitor.visitInsn(ICONST_1);
        Label label24 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label24);
        methodVisitor.visitLabel(label23);
        methodVisitor.visitFrame(
            Opcodes.F_APPEND, 1, new Object[] {"android/print/PrintDocumentInfo"}, 0, null);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitLabel(label24);
        methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {Opcodes.INTEGER});
        methodVisitor.visitVarInsn(ISTORE, 3);
        Label label25 = new Label();
        methodVisitor.visitLabel(label25);
        methodVisitor.visitLineNumber(483, label25);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "androidx/print/PrintHelper$PrintUriAdapter$1",
            "val$layoutResultCallback",
            "Landroid/print/PrintDocumentAdapter$LayoutResultCallback;");
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitVarInsn(ILOAD, 3);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "android/print/PrintDocumentAdapter$LayoutResultCallback",
            "onLayoutFinished",
            "(Landroid/print/PrintDocumentInfo;Z)V",
            false);
        Label label26 = new Label();
        methodVisitor.visitLabel(label26);
        methodVisitor.visitLineNumber(485, label26);
        Label label27 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label27);
        methodVisitor.visitLabel(label17);
        methodVisitor.visitLineNumber(486, label17);
        methodVisitor.visitFrame(Opcodes.F_CHOP, 1, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "androidx/print/PrintHelper$PrintUriAdapter$1",
            "val$layoutResultCallback",
            "Landroid/print/PrintDocumentAdapter$LayoutResultCallback;");
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "android/print/PrintDocumentAdapter$LayoutResultCallback",
            "onLayoutFailed",
            "(Ljava/lang/CharSequence;)V",
            false);
        methodVisitor.visitLabel(label27);
        methodVisitor.visitLineNumber(488, label27);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "androidx/print/PrintHelper$PrintUriAdapter$1",
            "this$1",
            "Landroidx/print/PrintHelper$PrintUriAdapter;");
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitFieldInsn(
            PUTFIELD,
            "androidx/print/PrintHelper$PrintUriAdapter",
            "mLoadBitmap",
            "Landroid/os/AsyncTask;");
        Label label28 = new Label();
        methodVisitor.visitLabel(label28);
        methodVisitor.visitLineNumber(489, label28);
        methodVisitor.visitInsn(RETURN);
        Label label29 = new Label();
        methodVisitor.visitLabel(label29);
        methodVisitor.visitLocalVariable(
            "rotation", "Landroid/graphics/Matrix;", null, label12, label6, 3);
        methodVisitor.visitLocalVariable(
            "mediaSize", "Landroid/print/PrintAttributes$MediaSize;", null, label8, label6, 2);
        methodVisitor.visitLocalVariable(
            "info", "Landroid/print/PrintDocumentInfo;", null, label22, label26, 2);
        methodVisitor.visitLocalVariable("changed", "Z", null, label25, label26, 3);
        methodVisitor.visitLocalVariable(
            "this", "Landroidx/print/PrintHelper$PrintUriAdapter$1;", null, label4, label29, 0);
        methodVisitor.visitLocalVariable(
            "bitmap", "Landroid/graphics/Bitmap;", null, label4, label29, 1);
        methodVisitor.visitMaxs(7, 5);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
