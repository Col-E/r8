// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Regression tests for b/237567012 */
@RunWith(Parameterized.class)
public class CfDebugLocalStackMapVerificationTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  static class SmallRepro {

    public static void main(String[] args) {
      RuntimeException x = new RuntimeException("FOO");
      RuntimeException c = null;
      try {
        c = x;
        throw c;
      } catch (RuntimeException e) {
        System.out.println(c);
      }
    }
  }

  @Test
  public void testReference() throws Exception {
    testForJvm()
        .addProgramClasses(SmallRepro.class)
        .run(parameters.getRuntime(), SmallRepro.class)
        .assertSuccessWithOutputThatMatches(containsString("FOO"));
  }

  @Test
  public void testSmallReproD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClasses(SmallRepro.class)
        .setMinApi(AndroidApiLevel.B)
        .addOptionsModification(
            options -> {
              options.testing.forceIRForCfToCfDesugar = true;
              options.testing.neverReuseCfLocalRegisters = true;
            })
        .run(parameters.getRuntime(), SmallRepro.class)
        // TODO(b/237567012): Run should succeed with printing of FOO.
        .assertFailureWithErrorThatThrows(VerifyError.class);
  }

  @Test
  public void testR8() throws Exception {
    // TODO(b/237567012): We should not fail compilation.
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addProgramClassFileData(CfDebugLocalStackMapVerificationTest$MainDump.dump())
                .addKeepMainRule(Main.class)
                .setMode(CompilationMode.DEBUG)
                .addDontWarn("*")
                // TODO(b/237567012): Remove option when resolved.
                .addOptionsModification(
                    options -> options.enableCheckAllInstructionsDuringStackMapVerification = true)
                .compile()
                .run(parameters.getRuntime(), Main.class));
  }

  public static class Main {

    // InvokeSuspend is taken from an input program.jar and copied verbatim in the dump below.
    public Object invokeSuspend(Object o) {
      return o;
    }

    public static void main(String[] args) {
      new Main().invokeSuspend(null);
    }
  }

  public static class CfDebugLocalStackMapVerificationTest$MainDump implements Opcodes {

    public static byte[] dump() throws Exception {

      ClassWriter classWriter = new ClassWriter(0);
      FieldVisitor fieldVisitor;
      MethodVisitor methodVisitor;
      AnnotationVisitor annotationVisitor0;

      classWriter.visit(
          V1_8,
          ACC_FINAL | ACC_SUPER,
          binaryName(Main.class),
          null,
          "kotlin/coroutines/jvm/internal/SuspendLambda",
          new String[] {"kotlin/jvm/functions/Function2"});

      {
        fieldVisitor = classWriter.visitField(0, "L$1", "Ljava/lang/Object;", null, null);
        fieldVisitor.visitEnd();
      }
      {
        fieldVisitor = classWriter.visitField(0, "L$2", "Ljava/lang/Object;", null, null);
        fieldVisitor.visitEnd();
      }
      {
        fieldVisitor = classWriter.visitField(0, "label", "I", null, null);
        fieldVisitor.visitEnd();
      }
      {
        fieldVisitor =
            classWriter.visitField(
                ACC_PRIVATE | ACC_SYNTHETIC, "L$0", "Ljava/lang/Object;", null, null);
        fieldVisitor.visitEnd();
      }
      {
        fieldVisitor =
            classWriter.visitField(
                ACC_FINAL | ACC_SYNTHETIC,
                "this$0",
                "Lio/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber;",
                null,
                null);
        fieldVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                0,
                "<init>",
                "(Lio/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber;Lkotlin/coroutines/Continuation;)V",
                "(Lio/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber;Lkotlin/coroutines/Continuation<-Lio/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1;>;)V",
                null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitFieldInsn(
            PUTFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "this$0",
            "Lio/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber;");
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitInsn(ICONST_2);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "kotlin/coroutines/jvm/internal/SuspendLambda",
            "<init>",
            "(ILkotlin/coroutines/Continuation;)V",
            false);
        methodVisitor.visitInsn(RETURN);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLocalVariable(
            "this",
            "Lio/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1;",
            null,
            label0,
            label1,
            0);
        methodVisitor.visitLocalVariable(
            "$receiver",
            "Lio/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber;",
            null,
            label0,
            label1,
            1);
        methodVisitor.visitLocalVariable(
            "$completion", "Lkotlin/coroutines/Continuation;", null, label0, label1, 2);
        methodVisitor.visitMaxs(3, 3);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_FINAL,
                "invokeSuspend",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                null,
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
        Label label0 = new Label();
        Label label1 = new Label();
        Label label2 = new Label();
        methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Throwable");
        Label label3 = new Label();
        Label label4 = new Label();
        methodVisitor.visitTryCatchBlock(label3, label4, label2, "java/lang/Throwable");
        Label label5 = new Label();
        Label label6 = new Label();
        methodVisitor.visitTryCatchBlock(label5, label6, label2, "java/lang/Throwable");
        Label label7 = new Label();
        methodVisitor.visitTryCatchBlock(label0, label1, label7, null);
        methodVisitor.visitTryCatchBlock(label3, label4, label7, null);
        methodVisitor.visitTryCatchBlock(label5, label6, label7, null);
        methodVisitor.visitTryCatchBlock(label2, label7, label7, null);
        Label label8 = new Label();
        methodVisitor.visitTryCatchBlock(label7, label8, label7, null);
        Label label9 = new Label();
        Label label10 = new Label();
        methodVisitor.visitTryCatchBlock(
            label9, label1, label10, "kotlinx/coroutines/channels/ClosedReceiveChannelException");
        methodVisitor.visitTryCatchBlock(
            label3, label4, label10, "kotlinx/coroutines/channels/ClosedReceiveChannelException");
        methodVisitor.visitTryCatchBlock(
            label5, label10, label10, "kotlinx/coroutines/channels/ClosedReceiveChannelException");
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "kotlin/coroutines/intrinsics/IntrinsicsKt",
            "getCOROUTINE_SUSPENDED",
            "()Ljava/lang/Object;",
            false);
        Label label11 = new Label();
        methodVisitor.visitLabel(label11);
        methodVisitor.visitLineNumber(60, label11);
        methodVisitor.visitVarInsn(ASTORE, 10);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "label",
            "I");
        Label label12 = new Label();
        Label label13 = new Label();
        Label label14 = new Label();
        Label label15 = new Label();
        methodVisitor.visitTableSwitchInsn(0, 2, label15, new Label[] {label12, label13, label14});
        methodVisitor.visitLabel(label12);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            11,
            new Object[] {
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
              "java/lang/Object",
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              "java/lang/Object"
            },
            0,
            new Object[] {});
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC, "kotlin/ResultKt", "throwOnFailure", "(Ljava/lang/Object;)V", false);
        Label label16 = new Label();
        methodVisitor.visitLabel(label16);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "L$0",
            "Ljava/lang/Object;");
        methodVisitor.visitTypeInsn(CHECKCAST, "kotlinx/coroutines/CoroutineScope");
        methodVisitor.visitVarInsn(ASTORE, 2);
        methodVisitor.visitLabel(label9);
        methodVisitor.visitLineNumber(61, label9);
        methodVisitor.visitInsn(NOP);
        Label label17 = new Label();
        methodVisitor.visitLabel(label17);
        methodVisitor.visitLineNumber(62, label17);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "this$0",
            "Lio/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber;");
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber",
            "access$getQueue$p",
            "(Lio/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber;)Lkotlinx/coroutines/channels/Channel;",
            false);
        methodVisitor.visitTypeInsn(CHECKCAST, "kotlinx/coroutines/channels/ReceiveChannel");
        methodVisitor.visitVarInsn(ASTORE, 3);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "this$0",
            "Lio/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber;");
        methodVisitor.visitVarInsn(ASTORE, 4);
        Label label18 = new Label();
        methodVisitor.visitLabel(label18);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 5);
        Label label19 = new Label();
        methodVisitor.visitLabel(label19);
        methodVisitor.visitLineNumber(146, label19);
        methodVisitor.visitInsn(NOP);
        Label label20 = new Label();
        methodVisitor.visitLabel(label20);
        methodVisitor.visitLineNumber(149, label20);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitVarInsn(ASTORE, 6);
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(150, label0);
        methodVisitor.visitInsn(NOP);
        Label label21 = new Label();
        methodVisitor.visitLabel(label21);
        methodVisitor.visitLineNumber(151, label21);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 7);
        Label label22 = new Label();
        methodVisitor.visitLabel(label22);
        methodVisitor.visitLineNumber(63, label22);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            11,
            new Object[] {
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
              "java/lang/Object",
              "kotlinx/coroutines/CoroutineScope",
              "kotlinx/coroutines/channels/ReceiveChannel",
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber",
              Opcodes.INTEGER,
              Opcodes.NULL,
              Opcodes.INTEGER,
              Opcodes.TOP,
              Opcodes.TOP,
              "java/lang/Object"
            },
            0,
            new Object[] {});
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "kotlinx/coroutines/CoroutineScopeKt",
            "isActive",
            "(Lkotlinx/coroutines/CoroutineScope;)Z",
            false);
        Label label23 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label23);
        Label label24 = new Label();
        methodVisitor.visitLabel(label24);
        methodVisitor.visitLineNumber(64, label24);
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber",
            "access$getQueue$p",
            "(Lio/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber;)Lkotlinx/coroutines/channels/Channel;",
            false);
        methodVisitor.visitMethodInsn(
            INVOKEINTERFACE,
            "kotlinx/coroutines/channels/Channel",
            "tryReceive-PtdJZtk",
            "()Ljava/lang/Object;",
            true);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "kotlinx/coroutines/channels/ChannelResult",
            "getOrNull-impl",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            false);
        methodVisitor.visitTypeInsn(CHECKCAST, "java/nio/ByteBuffer");
        methodVisitor.visitVarInsn(ASTORE, 8);
        Label label25 = new Label();
        methodVisitor.visitLabel(label25);
        methodVisitor.visitLineNumber(65, label25);
        methodVisitor.visitVarInsn(ALOAD, 8);
        Label label26 = new Label();
        methodVisitor.visitJumpInsn(IFNONNULL, label26);
        Label label27 = new Label();
        methodVisitor.visitLabel(label27);
        methodVisitor.visitLineNumber(66, label27);
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber",
            "subscription",
            "Ljava/lang/Object;");
        methodVisitor.visitTypeInsn(CHECKCAST, "java/util/concurrent/Flow$Subscription");
        methodVisitor.visitInsn(DUP);
        Label label28 = new Label();
        methodVisitor.visitJumpInsn(IFNULL, label28);
        methodVisitor.visitInsn(LCONST_1);
        methodVisitor.visitMethodInsn(
            INVOKEINTERFACE, "java/util/concurrent/Flow$Subscription", "request", "(J)V", true);
        Label label29 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label29);
        methodVisitor.visitLabel(label28);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            11,
            new Object[] {
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
              "java/lang/Object",
              "kotlinx/coroutines/CoroutineScope",
              "kotlinx/coroutines/channels/ReceiveChannel",
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber",
              Opcodes.INTEGER,
              Opcodes.NULL,
              Opcodes.INTEGER,
              "java/nio/ByteBuffer",
              Opcodes.TOP,
              "java/lang/Object"
            },
            1,
            new Object[] {"java/util/concurrent/Flow$Subscription"});
        methodVisitor.visitInsn(POP);
        methodVisitor.visitLabel(label29);
        methodVisitor.visitLineNumber(67, label29);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber",
            "access$getQueue$p",
            "(Lio/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber;)Lkotlinx/coroutines/channels/Channel;",
            false);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitFieldInsn(
            PUTFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "L$0",
            "Ljava/lang/Object;");
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitFieldInsn(
            PUTFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "L$1",
            "Ljava/lang/Object;");
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitFieldInsn(
            PUTFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "L$2",
            "Ljava/lang/Object;");
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitFieldInsn(
            PUTFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "label",
            "I");
        methodVisitor.visitMethodInsn(
            INVOKEINTERFACE,
            "kotlinx/coroutines/channels/Channel",
            "receive",
            "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;",
            true);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitVarInsn(ALOAD, 10);
        Label label30 = new Label();
        methodVisitor.visitJumpInsn(IF_ACMPNE, label30);
        Label label31 = new Label();
        methodVisitor.visitLabel(label31);
        methodVisitor.visitLineNumber(60, label31);
        methodVisitor.visitVarInsn(ALOAD, 10);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitLabel(label13);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            11,
            new Object[] {
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
              "java/lang/Object",
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              "java/lang/Object"
            },
            0,
            new Object[] {});
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 5);
        Label label32 = new Label();
        methodVisitor.visitLabel(label32);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 7);
        Label label33 = new Label();
        methodVisitor.visitLabel(label33);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitVarInsn(ASTORE, 6);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "L$2",
            "Ljava/lang/Object;");
        methodVisitor.visitTypeInsn(
            CHECKCAST,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber");
        methodVisitor.visitVarInsn(ASTORE, 4);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "L$1",
            "Ljava/lang/Object;");
        methodVisitor.visitTypeInsn(CHECKCAST, "kotlinx/coroutines/channels/ReceiveChannel");
        methodVisitor.visitVarInsn(ASTORE, 3);
        Label label34 = new Label();
        methodVisitor.visitLabel(label34);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "L$0",
            "Ljava/lang/Object;");
        methodVisitor.visitTypeInsn(CHECKCAST, "kotlinx/coroutines/CoroutineScope");
        methodVisitor.visitVarInsn(ASTORE, 2);
        methodVisitor.visitLabel(label3);
        methodVisitor.visitInsn(NOP);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC, "kotlin/ResultKt", "throwOnFailure", "(Ljava/lang/Object;)V", false);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitLabel(label30);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            11,
            new Object[] {
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
              "java/lang/Object",
              "kotlinx/coroutines/CoroutineScope",
              "kotlinx/coroutines/channels/ReceiveChannel",
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber",
              Opcodes.INTEGER,
              Opcodes.NULL,
              Opcodes.INTEGER,
              Opcodes.TOP,
              Opcodes.TOP,
              "java/lang/Object"
            },
            1,
            new Object[] {"java/lang/Object"});
        methodVisitor.visitTypeInsn(CHECKCAST, "java/nio/ByteBuffer");
        methodVisitor.visitVarInsn(ASTORE, 8);
        methodVisitor.visitLabel(label26);
        methodVisitor.visitLineNumber(70, label26);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            11,
            new Object[] {
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
              "java/lang/Object",
              "kotlinx/coroutines/CoroutineScope",
              "kotlinx/coroutines/channels/ReceiveChannel",
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber",
              Opcodes.INTEGER,
              Opcodes.NULL,
              Opcodes.INTEGER,
              "java/nio/ByteBuffer",
              Opcodes.TOP,
              "java/lang/Object"
            },
            0,
            new Object[] {});
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber",
            "access$getResponseChannel$p",
            "(Lio/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber;)Lio/ktor/utils/io/ByteChannel;",
            false);
        methodVisitor.visitVarInsn(ALOAD, 8);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitFieldInsn(
            PUTFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "L$0",
            "Ljava/lang/Object;");
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitFieldInsn(
            PUTFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "L$1",
            "Ljava/lang/Object;");
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 4);
        methodVisitor.visitFieldInsn(
            PUTFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "L$2",
            "Ljava/lang/Object;");
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitInsn(ICONST_2);
        methodVisitor.visitFieldInsn(
            PUTFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "label",
            "I");
        methodVisitor.visitMethodInsn(
            INVOKEINTERFACE,
            "io/ktor/utils/io/ByteChannel",
            "writeFully",
            "(Ljava/nio/ByteBuffer;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;",
            true);
        methodVisitor.visitLabel(label4);
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitVarInsn(ALOAD, 10);
        Label label35 = new Label();
        methodVisitor.visitJumpInsn(IF_ACMPNE, label35);
        Label label36 = new Label();
        methodVisitor.visitLabel(label36);
        methodVisitor.visitLineNumber(60, label36);
        methodVisitor.visitVarInsn(ALOAD, 10);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitLabel(label14);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            11,
            new Object[] {
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
              "java/lang/Object",
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              "java/lang/Object"
            },
            0,
            new Object[] {});
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 5);
        Label label37 = new Label();
        methodVisitor.visitLabel(label37);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitVarInsn(ISTORE, 7);
        Label label38 = new Label();
        methodVisitor.visitLabel(label38);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitVarInsn(ASTORE, 6);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "L$2",
            "Ljava/lang/Object;");
        methodVisitor.visitTypeInsn(
            CHECKCAST,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber");
        methodVisitor.visitVarInsn(ASTORE, 4);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "L$1",
            "Ljava/lang/Object;");
        methodVisitor.visitTypeInsn(CHECKCAST, "kotlinx/coroutines/channels/ReceiveChannel");
        methodVisitor.visitVarInsn(ASTORE, 3);
        Label label39 = new Label();
        methodVisitor.visitLabel(label39);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
            "L$0",
            "Ljava/lang/Object;");
        methodVisitor.visitTypeInsn(CHECKCAST, "kotlinx/coroutines/CoroutineScope");
        methodVisitor.visitVarInsn(ASTORE, 2);
        methodVisitor.visitLabel(label5);
        methodVisitor.visitInsn(NOP);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC, "kotlin/ResultKt", "throwOnFailure", "(Ljava/lang/Object;)V", false);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitLabel(label35);
        methodVisitor.visitLineNumber(70, label35);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            11,
            new Object[] {
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
              "java/lang/Object",
              "kotlinx/coroutines/CoroutineScope",
              "kotlinx/coroutines/channels/ReceiveChannel",
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber",
              Opcodes.INTEGER,
              Opcodes.NULL,
              Opcodes.INTEGER,
              Opcodes.TOP,
              Opcodes.TOP,
              "java/lang/Object"
            },
            1,
            new Object[] {"java/lang/Object"});
        methodVisitor.visitInsn(POP);
        methodVisitor.visitJumpInsn(GOTO, label22);
        methodVisitor.visitLabel(label23);
        methodVisitor.visitLineNumber(72, label23);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitInsn(NOP);
        Label label40 = new Label();
        methodVisitor.visitLabel(label40);
        methodVisitor.visitFieldInsn(GETSTATIC, "kotlin/Unit", "INSTANCE", "Lkotlin/Unit;");
        methodVisitor.visitVarInsn(ASTORE, 9);
        methodVisitor.visitLabel(label6);
        methodVisitor.visitLineNumber(156, label6);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "kotlinx/coroutines/channels/ChannelsKt",
            "cancelConsumed",
            "(Lkotlinx/coroutines/channels/ReceiveChannel;Ljava/lang/Throwable;)V",
            false);
        Label label41 = new Label();
        methodVisitor.visitLabel(label41);
        methodVisitor.visitLineNumber(151, label41);
        Label label42 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label42);
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLineNumber(152, label2);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            11,
            new Object[] {
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
              "java/lang/Object",
              "kotlinx/coroutines/CoroutineScope",
              "kotlinx/coroutines/channels/ReceiveChannel",
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber",
              Opcodes.INTEGER,
              Opcodes.NULL,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              "java/lang/Object"
            },
            1,
            new Object[] {"java/lang/Throwable"});
        methodVisitor.visitVarInsn(ASTORE, 9);
        Label label43 = new Label();
        methodVisitor.visitLabel(label43);
        methodVisitor.visitLineNumber(153, label43);
        methodVisitor.visitVarInsn(ALOAD, 9);
        methodVisitor.visitVarInsn(ASTORE, 6);
        Label label44 = new Label();
        methodVisitor.visitLabel(label44);
        methodVisitor.visitLineNumber(154, label44);
        methodVisitor.visitVarInsn(ALOAD, 9);
        methodVisitor.visitInsn(ATHROW);
        methodVisitor.visitLabel(label7);
        methodVisitor.visitLineNumber(155, label7);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            11,
            new Object[] {
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
              "java/lang/Object",
              "kotlinx/coroutines/CoroutineScope",
              "kotlinx/coroutines/channels/ReceiveChannel",
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber",
              Opcodes.INTEGER,
              "java/lang/Throwable",
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              "java/lang/Object"
            },
            1,
            new Object[] {"java/lang/Throwable"});
        methodVisitor.visitVarInsn(ASTORE, 9);
        methodVisitor.visitLabel(label8);
        methodVisitor.visitLineNumber(156, label8);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitVarInsn(ALOAD, 6);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "kotlinx/coroutines/channels/ChannelsKt",
            "cancelConsumed",
            "(Lkotlinx/coroutines/channels/ReceiveChannel;Ljava/lang/Throwable;)V",
            false);
        methodVisitor.visitVarInsn(ALOAD, 9);
        methodVisitor.visitInsn(ATHROW);
        methodVisitor.visitLabel(label10);
        methodVisitor.visitLineNumber(73, label10);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            11,
            new Object[] {
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
              "java/lang/Object",
              "kotlinx/coroutines/CoroutineScope",
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              "java/lang/Object"
            },
            1,
            new Object[] {"kotlinx/coroutines/channels/ClosedReceiveChannelException"});
        methodVisitor.visitVarInsn(ASTORE, 3);
        methodVisitor.visitLabel(label42);
        methodVisitor.visitLineNumber(75, label42);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            11,
            new Object[] {
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
              "java/lang/Object",
              "kotlinx/coroutines/CoroutineScope",
              "java/lang/Object",
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              "java/lang/Object"
            },
            0,
            new Object[] {});
        methodVisitor.visitFieldInsn(GETSTATIC, "kotlin/Unit", "INSTANCE", "Lkotlin/Unit;");
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitLabel(label15);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            11,
            new Object[] {
              "io/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1",
              "java/lang/Object",
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
              Opcodes.TOP,
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
        methodVisitor.visitLocalVariable(
            "$this$launch", "Lkotlinx/coroutines/CoroutineScope;", null, label9, label13, 2);
        methodVisitor.visitLocalVariable(
            "$this$launch", "Lkotlinx/coroutines/CoroutineScope;", null, label3, label14, 2);
        methodVisitor.visitLocalVariable(
            "$this$launch", "Lkotlinx/coroutines/CoroutineScope;", null, label5, label23, 2);
        methodVisitor.visitLocalVariable(
            "$this$consume$iv",
            "Lkotlinx/coroutines/channels/ReceiveChannel;",
            null,
            label18,
            label13,
            3);
        methodVisitor.visitLocalVariable(
            "$this$consume$iv",
            "Lkotlinx/coroutines/channels/ReceiveChannel;",
            null,
            label34,
            label14,
            3);
        methodVisitor.visitLocalVariable(
            "$this$consume$iv",
            "Lkotlinx/coroutines/channels/ReceiveChannel;",
            null,
            label39,
            label40,
            3);
        methodVisitor.visitLocalVariable(
            "$this$consume$iv",
            "Lkotlinx/coroutines/channels/ReceiveChannel;",
            null,
            label40,
            label41,
            3);
        methodVisitor.visitLocalVariable(
            "$this$consume$iv",
            "Lkotlinx/coroutines/channels/ReceiveChannel;",
            null,
            label2,
            label10,
            3);
        methodVisitor.visitLocalVariable(
            "cause$iv", "Ljava/lang/Throwable;", null, label0, label13, 6);
        methodVisitor.visitLocalVariable(
            "cause$iv", "Ljava/lang/Throwable;", null, label34, label14, 6);
        methodVisitor.visitLocalVariable(
            "cause$iv", "Ljava/lang/Throwable;", null, label39, label40, 6);
        methodVisitor.visitLocalVariable(
            "cause$iv", "Ljava/lang/Throwable;", null, label40, label41, 6);
        methodVisitor.visitLocalVariable(
            "cause$iv", "Ljava/lang/Throwable;", null, label2, label44, 6);
        methodVisitor.visitLocalVariable(
            "cause$iv", "Ljava/lang/Throwable;", null, label44, label10, 6);
        methodVisitor.visitLocalVariable(
            "buffer", "Ljava/nio/ByteBuffer;", null, label25, label27, 8);
        methodVisitor.visitLocalVariable(
            "buffer", "Ljava/nio/ByteBuffer;", null, label26, label4, 8);
        methodVisitor.visitLocalVariable("e$iv", "Ljava/lang/Throwable;", null, label43, label7, 9);
        methodVisitor.visitLocalVariable(
            "$i$a$-consume-JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1$1",
            "I",
            null,
            label22,
            label13,
            7);
        methodVisitor.visitLocalVariable("$i$f$consume", "I", null, label19, label13, 5);
        methodVisitor.visitLocalVariable(
            "this",
            "Lio/ktor/client/engine/java/JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1;",
            null,
            label16,
            label15,
            0);
        methodVisitor.visitLocalVariable(
            "$result", "Ljava/lang/Object;", null, label16, label15, 1);
        methodVisitor.visitLocalVariable(
            "$i$a$-consume-JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1$1",
            "I",
            null,
            label33,
            label14,
            7);
        methodVisitor.visitLocalVariable("$i$f$consume", "I", null, label32, label14, 5);
        methodVisitor.visitLocalVariable(
            "$i$a$-consume-JavaHttpResponseBodyHandler$JavaHttpResponseBodySubscriber$1$1",
            "I",
            null,
            label38,
            label40,
            7);
        methodVisitor.visitLocalVariable("$i$f$consume", "I", null, label37, label10, 5);
        methodVisitor.visitMaxs(5, 11);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(70, label0);
        methodVisitor.visitTypeInsn(
            NEW, "com/android/tools/r8/cf/CfDebugLocalStackMapVerificationTest$Main");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/android/tools/r8/cf/CfDebugLocalStackMapVerificationTest$Main",
            "<init>",
            "()V",
            false);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/android/tools/r8/cf/CfDebugLocalStackMapVerificationTest$Main",
            "invokeSuspend",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            false);
        methodVisitor.visitInsn(POP);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(71, label1);
        methodVisitor.visitInsn(RETURN);
        Label label2 = new Label();
        methodVisitor.visitLabel(label2);
        methodVisitor.visitLocalVariable("args", "[Ljava/lang/String;", null, label0, label2, 0);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
