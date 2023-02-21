// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class DebugLocalStackMapTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DebugLocalStackMapTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testInvalidDebugLocals() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(TestKtDump.dump())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertInfosMatch(
                  ImmutableList.of(
                      diagnosticMessage(
                          containsString("Stripped invalid locals information from 1 method")),
                      diagnosticMessage(
                          allOf(
                              containsString(
                                  "java.lang.Object"
                                      + " TestKt.suspendHere(kotlin.coroutines.Continuation)"),
                              containsString(
                                  "Information in locals-table is invalid with respect to the"
                                      + " stack map table"))),
                      diagnosticMessage(
                          containsString(
                              "Some warnings are typically a sign of using an outdated Java"
                                  + " toolchain"))));
            });
  }

  public static class TestKtDump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;
      AnnotationVisitor annotationVisitor0;

      classWriter.visit(
          V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, "TestKt", null, "java/lang/Object", null);

      classWriter.visitSource(
          "test.kt",
          "SMAP\n"
              + "test.kt\n"
              + "Kotlin\n"
              + "*S Kotlin\n"
              + "*F\n"
              + "+ 1 test.kt\n"
              + "TestKt\n"
              + "*L\n"
              + "1#1,17:1\n"
              + "4#1,4:18\n"
              + "10#1:22\n"
              + "4#1,4:23\n"
              + "*S KotlinDebug\n"
              + "*F\n"
              + "+ 1 test.kt\n"
              + "TestKt\n"
              + "*L\n"
              + "10#1:18,4\n"
              + "12#1:22\n"
              + "12#1:23,4\n"
              + "*E\n");

      {
        annotationVisitor0 = classWriter.visitAnnotation("Lkotlin/Metadata;", true);
        annotationVisitor0.visit("mv", new int[] {1, 5, 0});
        annotationVisitor0.visit("k", new Integer(2));
        annotationVisitor0.visit("xi", new Integer(50));
        {
          AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d1");
          annotationVisitor1.visit(
              null,
              "\u0000\u0012\n"
                  + "\u0000\n"
                  + "\u0002\u0010\u0002\n"
                  + "\u0002\u0008\u0002\n"
                  + "\u0002\u0010\u000e\n"
                  + "\u0002\u0008\u0004\u001a\u0011\u0010\u0000\u001a\u00020\u0001H\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u0002\u001a\u0011\u0010\u0003\u001a\u00020\u0004H\u0086@\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u0002\u001a\u0011\u0010\u0005\u001a\u00020\u0004H\u0086H\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u0002\u001a\u0019\u0010\u0006\u001a\u00020\u00042\u0006\u0010\u0007\u001a\u00020\u0004H\u0086H\u00f8\u0001\u0000\u00a2\u0006\u0002\u0010\u0008\u0082\u0002\u0004\n"
                  + "\u0002\u0008\u0019");
          annotationVisitor1.visitEnd();
        }
        {
          AnnotationVisitor annotationVisitor1 = annotationVisitor0.visitArray("d2");
          annotationVisitor1.visit(null, "main");
          annotationVisitor1.visit(null, "");
          annotationVisitor1.visit(null, "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;");
          annotationVisitor1.visit(null, "mainSuspend");
          annotationVisitor1.visit(null, "");
          annotationVisitor1.visit(null, "suspendHere");
          annotationVisitor1.visit(null, "suspendThere");
          annotationVisitor1.visit(null, "v");
          annotationVisitor1.visit(
              null, "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;");
          annotationVisitor1.visitEnd();
        }
        annotationVisitor0.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_FINAL | ACC_STATIC,
                "suspendHere",
                "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;",
                "(Lkotlin/coroutines/Continuation<-Ljava/lang/String;>;)Ljava/lang/Object;",
                null);
        {
          annotationVisitor0 =
              methodVisitor.visitAnnotation("Lorg/jetbrains/annotations/Nullable;", false);
          annotationVisitor0.visitEnd();
        }
        methodVisitor.visitAnnotableParameterCount(1, false);
        {
          annotationVisitor0 =
              methodVisitor.visitParameterAnnotation(
                  0, "Lorg/jetbrains/annotations/NotNull;", false);
          annotationVisitor0.visitEnd();
        }
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitTypeInsn(INSTANCEOF, "TestKt$suspendHere$1");
        Label label0 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitTypeInsn(CHECKCAST, "TestKt$suspendHere$1");
        methodVisitor.visitVarInsn(ASTORE, 13);
        methodVisitor.visitVarInsn(ALOAD, 13);
        methodVisitor.visitFieldInsn(GETFIELD, "TestKt$suspendHere$1", "label", "I");
        methodVisitor.visitLdcInsn(new Integer(-2147483648));
        methodVisitor.visitInsn(IAND);
        methodVisitor.visitJumpInsn(IFEQ, label0);
        methodVisitor.visitVarInsn(ALOAD, 13);
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitFieldInsn(GETFIELD, "TestKt$suspendHere$1", "label", "I");
        methodVisitor.visitLdcInsn(new Integer(-2147483648));
        methodVisitor.visitInsn(ISUB);
        methodVisitor.visitFieldInsn(PUTFIELD, "TestKt$suspendHere$1", "label", "I");
        Label label1 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label1);
        methodVisitor.visitLabel(label0);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitTypeInsn(NEW, "TestKt$suspendHere$1");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "TestKt$suspendHere$1",
            "<init>",
            "(Lkotlin/coroutines/Continuation;)V",
            false);
        methodVisitor.visitVarInsn(ASTORE, 13);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            14,
            new Object[] {
              "kotlin/coroutines/Continuation",
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              "TestKt$suspendHere$1"
            },
            0,
            new Object[] {});
        methodVisitor.visitVarInsn(ALOAD, 13);
        methodVisitor.visitFieldInsn(
            GETFIELD, "TestKt$suspendHere$1", "result", "Ljava/lang/Object;");
        methodVisitor.visitVarInsn(ASTORE, 12);
        Label label2 = new Label();
        methodVisitor.visitLabel(label2);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "kotlin/coroutines/intrinsics/IntrinsicsKt",
            "getCOROUTINE_SUSPENDED",
            "()Ljava/lang/Object;",
            false);
        Label label3 = new Label();
        methodVisitor.visitLabel(label3);
        methodVisitor.visitLineNumber(10, label3);
        methodVisitor.visitVarInsn(ASTORE, 14);
        methodVisitor.visitVarInsn(ALOAD, 13);
        methodVisitor.visitFieldInsn(GETFIELD, "TestKt$suspendHere$1", "label", "I");
        Label label4 = new Label();
        Label label5 = new Label();
        Label label6 = new Label();
        Label label7 = new Label();
        methodVisitor.visitTableSwitchInsn(0, 2, label7, new Label[] {label4, label5, label6});
        methodVisitor.visitLabel(label4);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            15,
            new Object[] {
              "kotlin/coroutines/Continuation",
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              "java/lang/Object",
              "TestKt$suspendHere$1",
              "java/lang/Object"
            },
            0,
            new Object[] {});
        methodVisitor.visitVarInsn(ALOAD, 12);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC, "kotlin/ResultKt", "throwOnFailure", "(Ljava/lang/Object;)V", false);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 1);
        Label label8 = new Label();
        methodVisitor.visitLabel(label8);
        methodVisitor.visitLineNumber(10, label8);
        methodVisitor.visitLdcInsn("O");
        methodVisitor.visitVarInsn(ASTORE, 2);
        Label label9 = new Label();
        methodVisitor.visitLabel(label9);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 3);
        Label label10 = new Label();
        methodVisitor.visitLabel(label10);
        methodVisitor.visitLineNumber(18, label10);
        methodVisitor.visitVarInsn(ALOAD, 13);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitFieldInsn(PUTFIELD, "TestKt$suspendHere$1", "L$0", "Ljava/lang/Object;");
        methodVisitor.visitVarInsn(ALOAD, 13);
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitFieldInsn(PUTFIELD, "TestKt$suspendHere$1", "label", "I");
        methodVisitor.visitVarInsn(ALOAD, 13);
        methodVisitor.visitTypeInsn(CHECKCAST, "kotlin/coroutines/Continuation");
        methodVisitor.visitVarInsn(ASTORE, 4);
        Label label11 = new Label();
        methodVisitor.visitLabel(label11);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 5);
        Label label12 = new Label();
        methodVisitor.visitLabel(label12);
        methodVisitor.visitLineNumber(19, label12);
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitVarInsn(ASTORE, 6);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 7);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitFieldInsn(
            GETSTATIC, "kotlin/Result", "Companion", "Lkotlin/Result$Companion;");
        methodVisitor.visitVarInsn(ASTORE, 8);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 9);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "kotlin/Result",
            "constructor-impl",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            false);
        methodVisitor.visitMethodInsn(
            INVOKEINTERFACE,
            "kotlin/coroutines/Continuation",
            "resumeWith",
            "(Ljava/lang/Object;)V",
            true);
        Label label13 = new Label();
        methodVisitor.visitLabel(label13);
        methodVisitor.visitLineNumber(20, label13);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "kotlin/coroutines/intrinsics/IntrinsicsKt",
            "getCOROUTINE_SUSPENDED",
            "()Ljava/lang/Object;",
            false);
        Label label14 = new Label();
        methodVisitor.visitLabel(label14);
        methodVisitor.visitLineNumber(18, label14);
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "kotlin/coroutines/intrinsics/IntrinsicsKt",
            "getCOROUTINE_SUSPENDED",
            "()Ljava/lang/Object;",
            false);
        Label label15 = new Label();
        methodVisitor.visitJumpInsn(IF_ACMPNE, label15);
        methodVisitor.visitVarInsn(ALOAD, 13);
        methodVisitor.visitTypeInsn(CHECKCAST, "kotlin/coroutines/Continuation");
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "kotlin/coroutines/jvm/internal/DebugProbesKt",
            "probeCoroutineSuspended",
            "(Lkotlin/coroutines/Continuation;)V",
            false);
        methodVisitor.visitLabel(label15);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            15,
            new Object[] {
              "kotlin/coroutines/Continuation",
              Opcodes.INTEGER,
              "java/lang/String",
              Opcodes.INTEGER,
              "kotlin/coroutines/Continuation",
              Opcodes.INTEGER,
              "kotlin/coroutines/Continuation",
              Opcodes.INTEGER,
              "kotlin/Result$Companion",
              Opcodes.INTEGER,
              Opcodes.TOP,
              Opcodes.TOP,
              "java/lang/Object",
              "TestKt$suspendHere$1",
              "java/lang/Object"
            },
            1,
            new Object[] {"java/lang/Object"});
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitVarInsn(ALOAD, 14);
        Label label16 = new Label();
        methodVisitor.visitJumpInsn(IF_ACMPNE, label16);
        Label label17 = new Label();
        methodVisitor.visitLabel(label17);
        methodVisitor.visitLineNumber(10, label17);
        methodVisitor.visitVarInsn(ALOAD, 14);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitLabel(label5);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            15,
            new Object[] {
              "kotlin/coroutines/Continuation",
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              "java/lang/Object",
              "TestKt$suspendHere$1",
              "java/lang/Object"
            },
            0,
            new Object[] {});
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 1);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 3);
        methodVisitor.visitVarInsn(ALOAD, 13);
        methodVisitor.visitFieldInsn(GETFIELD, "TestKt$suspendHere$1", "L$0", "Ljava/lang/Object;");
        methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
        methodVisitor.visitVarInsn(ASTORE, 2);
        methodVisitor.visitVarInsn(ALOAD, 12);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC, "kotlin/ResultKt", "throwOnFailure", "(Ljava/lang/Object;)V", false);
        methodVisitor.visitVarInsn(ALOAD, 12);
        methodVisitor.visitLabel(label16);
        methodVisitor.visitLineNumber(21, label16);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            15,
            new Object[] {
              "kotlin/coroutines/Continuation",
              Opcodes.INTEGER,
              "java/lang/String",
              Opcodes.INTEGER,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              "java/lang/Object",
              "TestKt$suspendHere$1",
              "java/lang/Object"
            },
            1,
            new Object[] {"java/lang/Object"});
        methodVisitor.visitInsn(NOP);
        Label label18 = new Label();
        methodVisitor.visitLabel(label18);
        methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
        Label label19 = new Label();
        methodVisitor.visitLabel(label19);
        methodVisitor.visitLineNumber(10, label19);
        methodVisitor.visitLdcInsn("K");
        methodVisitor.visitVarInsn(ASTORE, 2);
        methodVisitor.visitVarInsn(ASTORE, 10);
        Label label20 = new Label();
        methodVisitor.visitLabel(label20);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 3);
        Label label21 = new Label();
        methodVisitor.visitLabel(label21);
        methodVisitor.visitLineNumber(18, label21);
        methodVisitor.visitVarInsn(ALOAD, 13);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitFieldInsn(PUTFIELD, "TestKt$suspendHere$1", "L$0", "Ljava/lang/Object;");
        methodVisitor.visitVarInsn(ALOAD, 13);
        methodVisitor.visitVarInsn(ALOAD, 10);
        methodVisitor.visitFieldInsn(PUTFIELD, "TestKt$suspendHere$1", "L$1", "Ljava/lang/Object;");
        methodVisitor.visitVarInsn(ALOAD, 13);
        methodVisitor.visitInsn(ICONST_2);
        methodVisitor.visitFieldInsn(PUTFIELD, "TestKt$suspendHere$1", "label", "I");
        methodVisitor.visitVarInsn(ALOAD, 13);
        methodVisitor.visitTypeInsn(CHECKCAST, "kotlin/coroutines/Continuation");
        methodVisitor.visitVarInsn(ASTORE, 4);
        Label label22 = new Label();
        methodVisitor.visitLabel(label22);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 5);
        Label label23 = new Label();
        methodVisitor.visitLabel(label23);
        methodVisitor.visitLineNumber(19, label23);
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitVarInsn(ASTORE, 6);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 7);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitFieldInsn(
            GETSTATIC, "kotlin/Result", "Companion", "Lkotlin/Result$Companion;");
        methodVisitor.visitVarInsn(ASTORE, 8);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 9);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "kotlin/Result",
            "constructor-impl",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            false);
        methodVisitor.visitMethodInsn(
            INVOKEINTERFACE,
            "kotlin/coroutines/Continuation",
            "resumeWith",
            "(Ljava/lang/Object;)V",
            true);
        Label label24 = new Label();
        methodVisitor.visitLabel(label24);
        methodVisitor.visitLineNumber(20, label24);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "kotlin/coroutines/intrinsics/IntrinsicsKt",
            "getCOROUTINE_SUSPENDED",
            "()Ljava/lang/Object;",
            false);
        Label label25 = new Label();
        methodVisitor.visitLabel(label25);
        methodVisitor.visitLineNumber(18, label25);
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "kotlin/coroutines/intrinsics/IntrinsicsKt",
            "getCOROUTINE_SUSPENDED",
            "()Ljava/lang/Object;",
            false);
        Label label26 = new Label();
        methodVisitor.visitJumpInsn(IF_ACMPNE, label26);
        methodVisitor.visitVarInsn(ALOAD, 13);
        methodVisitor.visitTypeInsn(CHECKCAST, "kotlin/coroutines/Continuation");
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "kotlin/coroutines/jvm/internal/DebugProbesKt",
            "probeCoroutineSuspended",
            "(Lkotlin/coroutines/Continuation;)V",
            false);
        methodVisitor.visitLabel(label26);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            15,
            new Object[] {
              "kotlin/coroutines/Continuation",
              Opcodes.INTEGER,
              "java/lang/String",
              Opcodes.INTEGER,
              "kotlin/coroutines/Continuation",
              Opcodes.INTEGER,
              "kotlin/coroutines/Continuation",
              Opcodes.INTEGER,
              "kotlin/Result$Companion",
              Opcodes.INTEGER,
              "java/lang/String",
              Opcodes.TOP,
              "java/lang/Object",
              "TestKt$suspendHere$1",
              "java/lang/Object"
            },
            1,
            new Object[] {"java/lang/Object"});
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitVarInsn(ALOAD, 14);
        Label label27 = new Label();
        methodVisitor.visitJumpInsn(IF_ACMPNE, label27);
        Label label28 = new Label();
        methodVisitor.visitLabel(label28);
        methodVisitor.visitLineNumber(10, label28);
        methodVisitor.visitVarInsn(ALOAD, 14);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitLabel(label6);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            15,
            new Object[] {
              "kotlin/coroutines/Continuation",
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              "java/lang/Object",
              "TestKt$suspendHere$1",
              "java/lang/Object"
            },
            0,
            new Object[] {});
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 1);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 3);
        methodVisitor.visitVarInsn(ALOAD, 13);
        methodVisitor.visitFieldInsn(GETFIELD, "TestKt$suspendHere$1", "L$1", "Ljava/lang/Object;");
        methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
        methodVisitor.visitVarInsn(ASTORE, 10);
        methodVisitor.visitVarInsn(ALOAD, 13);
        methodVisitor.visitFieldInsn(GETFIELD, "TestKt$suspendHere$1", "L$0", "Ljava/lang/Object;");
        methodVisitor.visitTypeInsn(CHECKCAST, "java/lang/String");
        methodVisitor.visitVarInsn(ASTORE, 2);
        methodVisitor.visitVarInsn(ALOAD, 12);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC, "kotlin/ResultKt", "throwOnFailure", "(Ljava/lang/Object;)V", false);
        methodVisitor.visitVarInsn(ALOAD, 12);
        methodVisitor.visitLabel(label27);
        methodVisitor.visitLineNumber(21, label27);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            15,
            new Object[] {
              "kotlin/coroutines/Continuation",
              Opcodes.INTEGER,
              "java/lang/String",
              Opcodes.INTEGER,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              "java/lang/String",
              Opcodes.TOP,
              "java/lang/Object",
              "TestKt$suspendHere$1",
              "java/lang/Object"
            },
            1,
            new Object[] {"java/lang/Object"});
        methodVisitor.visitInsn(NOP);
        Label label29 = new Label();
        methodVisitor.visitLabel(label29);
        methodVisitor.visitVarInsn(ASTORE, 11);
        methodVisitor.visitVarInsn(ALOAD, 10);
        methodVisitor.visitVarInsn(ALOAD, 11);
        Label label30 = new Label();
        methodVisitor.visitLabel(label30);
        methodVisitor.visitLineNumber(10, label30);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "kotlin/jvm/internal/Intrinsics",
            "stringPlus",
            "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;",
            false);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitLabel(label7);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            15,
            new Object[] {
              "kotlin/coroutines/Continuation",
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              "java/lang/Object",
              "TestKt$suspendHere$1",
              "java/lang/Object"
            },
            0,
            new Object[] {});
        methodVisitor.visitTypeInsn(NEW, "java/lang/IllegalStateException");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitLdcInsn("call to 'resume' before 'invoke' with coroutine");
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "java/lang/IllegalStateException",
            "<init>",
            "(Ljava/lang/String;)V",
            false);
        methodVisitor.visitInsn(ATHROW);
        methodVisitor.visitLocalVariable("v$iv", "Ljava/lang/String;", null, label9, label15, 2);
        methodVisitor.visitLocalVariable("v$iv", "Ljava/lang/String;", null, label20, label26, 2);
        methodVisitor.visitLocalVariable(
            "x$iv", "Lkotlin/coroutines/Continuation;", null, label11, label14, 4);
        methodVisitor.visitLocalVariable(
            "x$iv", "Lkotlin/coroutines/Continuation;", null, label22, label25, 4);
        methodVisitor.visitLocalVariable(
            "$i$a$-suspendCoroutineUninterceptedOrReturn-TestKt$suspendThere$2$iv",
            "I",
            null,
            label12,
            label14,
            5);
        methodVisitor.visitLocalVariable("$i$f$suspendThere", "I", null, label10, label18, 3);
        methodVisitor.visitLocalVariable(
            "$i$a$-suspendCoroutineUninterceptedOrReturn-TestKt$suspendThere$2$iv",
            "I",
            null,
            label23,
            label25,
            5);
        methodVisitor.visitLocalVariable("$i$f$suspendThere", "I", null, label21, label29, 3);
        methodVisitor.visitLocalVariable("$i$f$suspendHere", "I", null, label8, label7, 1);
        methodVisitor.visitLocalVariable(
            "$continuation", "Lkotlin/coroutines/Continuation;", null, label1, label7, 13);
        methodVisitor.visitLocalVariable("$result", "Ljava/lang/Object;", null, label2, label7, 12);
        methodVisitor.visitMaxs(3, 15);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
