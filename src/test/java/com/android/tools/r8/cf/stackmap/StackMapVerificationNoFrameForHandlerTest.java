// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.stackmap;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.JvmTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class StackMapVerificationNoFrameForHandlerTest extends TestBase {

  private final TestParameters parameters;
  private final boolean includeFrameInHandler;
  private final String EXPECTED_OUTPUT = "Hello World!";
  private final String EXPECTED_VERIFY_ERROR =
      "Expected stack map table for method with non-linear control flow";
  private final String EXPECTED_JVM_ERROR =
      "java.lang.VerifyError: Expecting a stackmap frame at branch target";

  @Parameters(name = "{0}, include frame in handler: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public StackMapVerificationNoFrameForHandlerTest(
      TestParameters parameters, boolean includeFrameInHandler) {
    this.parameters = parameters;
    this.includeFrameInHandler = includeFrameInHandler;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    JvmTestRunResult mainResult =
        testForJvm(parameters)
            .addProgramClassFileData(
                includeFrameInHandler
                    ? MainDump.dump()
                    : transformer(MainDump.dump(), Reference.classFromClass(Main.class))
                        .stripFrames("main")
                        .transform())
            .run(parameters.getRuntime(), Main.class);
    if (includeFrameInHandler) {
      mainResult.assertSuccessWithOutputLines(EXPECTED_OUTPUT);
    } else {
      mainResult.assertFailureWithErrorThatMatches(containsString(EXPECTED_JVM_ERROR));
    }
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClassFileData(
            includeFrameInHandler
                ? MainDump.dump()
                : transformer(MainDump.dump(), Reference.classFromClass(Main.class))
                    .stripFrames("main")
                    .transform())
        .addOptionsModification(options -> options.testing.readInputStackMaps = true)
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(this::verifyWarningsRegardingStackMap)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testHandlerR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            includeFrameInHandler
                ? MainDump.dump()
                : transformer(MainDump.dump(), Reference.classFromClass(Main.class))
                    .stripFrames("main")
                    .transform())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .allowDiagnosticWarningMessages(!includeFrameInHandler)
        .addOptionsModification(
            options -> {
              options.getCfCodeAnalysisOptions().setEnableUnverifiableCodeReporting(false);
              options.testing.readInputStackMaps = true;
            })
        .compileWithExpectedDiagnostics(this::verifyWarningsRegardingStackMap)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  private void verifyWarningsRegardingStackMap(TestDiagnosticMessages diagnostics) {
    if (includeFrameInHandler) {
      diagnostics.assertNoMessages();
    } else {
      diagnostics.assertOnlyWarnings();
      diagnostics.assertWarningsMatch(diagnosticMessage(containsString(EXPECTED_VERIFY_ERROR)));
    }
  }

  public static class Main {

    public static void main(String[] args) {
      try {
        getThrowable(new Throwable());
      } catch (Throwable e) {
      }
      System.out.println("Hello World!");
    }

    public static Throwable getThrowable(Throwable throwable) {
      if (System.currentTimeMillis() > 0) {
        return new RuntimeException(throwable);
      } else {
        throw new ClassCastException();
      }
    }
  }

  /**
   * The dump is mostly the code obtained from the Main class above, however, some instructions are
   * removed to have the frames being the same with linear flow:
   *
   * <pre>
   * try {
   * getThrowable(new Throwable());
   * pop
   * goto lbl3
   * } catch (Throwable e) {
   *   astore(1);
   *   goto lbl3
   * }
   * lbl3
   * System.out.println("Hello World!");
   * </pre>
   *
   * becomes:
   *
   * <pre>
   * try {
   * getThrowable(new Throwable());
   * } catch (Throwable e) {
   *   pop;
   * }
   * lbl3
   * System.out.println("Hello World!");
   * </pre>
   */
  public static class MainDump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_PUBLIC | ACC_SUPER,
          "com/android/tools/r8/cf/stackmap/StackMapVerificationNoFrameForHandlerTest$Main",
          null,
          "java/lang/Object",
          null);

      classWriter.visitInnerClass(
          "com/android/tools/r8/cf/stackmap/StackMapVerificationNoFrameForHandlerTest$Main",
          "com/android/tools/r8/cf/stackmap/StackMapVerificationNoFrameForHandlerTest",
          "Main",
          ACC_PUBLIC | ACC_STATIC);

      {
        methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        Label label1 = new Label();
        Label label2 = new Label();
        methodVisitor.visitTryCatchBlock(label0, label1, label2, "java/lang/Throwable");
        methodVisitor.visitLabel(label0);
        methodVisitor.visitTypeInsn(NEW, "java/lang/Throwable");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Throwable", "<init>", "()V", false);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "com/android/tools/r8/cf/stackmap/StackMapVerificationNoFrameForHandlerTest$Main",
            "getThrowable",
            "(Ljava/lang/Throwable;)Ljava/lang/Throwable;",
            false);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLabel(label2);
        methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
        methodVisitor.visitInsn(POP);
        Label label3 = new Label();
        methodVisitor.visitLabel(label3);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("Hello World!");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(2, 2);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC,
                "getThrowable",
                "(Ljava/lang/Throwable;)Ljava/lang/Throwable;",
                null,
                null);
        methodVisitor.visitCode();
        methodVisitor.visitMethodInsn(
            INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false);
        methodVisitor.visitInsn(LCONST_0);
        methodVisitor.visitInsn(LCMP);
        Label label0 = new Label();
        methodVisitor.visitJumpInsn(IFLE, label0);
        methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "java/lang/RuntimeException",
            "<init>",
            "(Ljava/lang/Throwable;)V",
            false);
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitLabel(label0);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitTypeInsn(NEW, "java/lang/ClassCastException");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL, "java/lang/ClassCastException", "<init>", "()V", false);
        methodVisitor.visitInsn(ATHROW);
        methodVisitor.visitMaxs(4, 1);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
