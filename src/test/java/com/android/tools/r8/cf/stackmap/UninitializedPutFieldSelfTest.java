// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.stackmap;

import static com.android.tools.r8.cf.stackmap.UninitializedPutFieldSelfTest.MainDump.dump;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class UninitializedPutFieldSelfTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(dump())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(VerifyError.class);
  }

  @Test(expected = CompilationFailedException.class)
  public void testD8Cf() throws Exception {
    parameters.assumeCfRuntime();
    testForD8(parameters.getBackend())
        .addProgramClassFileData(dump())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(this::inspect);
  }

  @Test
  public void testD8Dex() throws Exception {
    parameters.assumeDexRuntime();
    boolean willFailVerification =
        parameters.getDexRuntimeVersion().isOlderThan(Version.V5_1_1)
            || parameters.getDexRuntimeVersion().isNewerThan(Version.V6_0_1);
    testForD8()
        .addProgramClassFileData(dump())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrowsIf(willFailVerification, VerifyError.class)
        .assertSuccessWithOutputLinesIf(!willFailVerification, "Main::foo");
  }

  private void inspect(TestDiagnosticMessages diagnostics) {
    diagnostics.assertWarningMessageThatMatches(
        containsString(
            "Expected initialized "
                + Main.class.getTypeName()
                + " on stack, but was uninitialized-this"));
    if (parameters.isCfRuntime()) {
      diagnostics.assertErrorMessageThatMatches(
          containsString("Could not validate stack map frames"));
    }
  }

  public static class Main {

    private Main main;

    private Main() {
      this.main = this;
      this.main.foo();
    }

    private void foo() {
      System.out.println("Main::foo");
    }

    public static void main(String[] args) {
      new Main();
    }
  }

  // The dump is generated from the above code. The change that is made is to Main::<init> where we
  // now putfield before initializing. That will try and assign an uninstantiated type to a field
  // which is not allowed.
  public static class MainDump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      FieldVisitor fieldVisitor;
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_PUBLIC | ACC_SUPER,
          "com/android/tools/r8/cf/stackmap/UninitializedPutFieldSelfTest$Main",
          null,
          "java/lang/Object",
          null);

      classWriter.visitInnerClass(
          "com/android/tools/r8/cf/stackmap/UninitializedPutFieldSelfTest$Main",
          "com/android/tools/r8/cf/stackmap/UninitializedPutFieldSelfTest",
          "Main",
          ACC_PUBLIC | ACC_STATIC);

      {
        fieldVisitor =
            classWriter.visitField(
                ACC_PRIVATE,
                "main",
                "Lcom/android/tools/r8/cf/stackmap/UninitializedPutFieldSelfTest$Main;",
                null,
                null);
        fieldVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            PUTFIELD,
            "com/android/tools/r8/cf/stackmap/UninitializedPutFieldSelfTest$Main",
            "main",
            "Lcom/android/tools/r8/cf/stackmap/UninitializedPutFieldSelfTest$Main;");
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "com/android/tools/r8/cf/stackmap/UninitializedPutFieldSelfTest$Main",
            "main",
            "Lcom/android/tools/r8/cf/stackmap/UninitializedPutFieldSelfTest$Main;");
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/android/tools/r8/cf/stackmap/UninitializedPutFieldSelfTest$Main",
            "foo",
            "()V",
            false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "foo", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("Main::foo");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitTypeInsn(
            NEW, "com/android/tools/r8/cf/stackmap/UninitializedPutFieldSelfTest$Main");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/android/tools/r8/cf/stackmap/UninitializedPutFieldSelfTest$Main",
            "<init>",
            "()V",
            false);
        methodVisitor.visitInsn(POP);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
