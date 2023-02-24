// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.stackmap;

import static com.android.tools.r8.cf.stackmap.UninitializedInstanceOfTest.MainDump.dump;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class UninitializedInstanceOfTest extends TestBase {

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

  @Test()
  public void testD8Dex() throws Exception {
    parameters.assumeDexRuntime();
    boolean expectFailure = parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V7_0_0);
    testForD8(parameters.getBackend())
        .addProgramClassFileData(dump())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            expectFailure,
            result -> result.assertFailureWithErrorThatThrows(VerifyError.class),
            TestRunResult::assertSuccessWithOutputLines);
  }

  private void inspect(TestDiagnosticMessages diagnostics) {
    diagnostics.assertWarningMessageThatMatches(
        containsString(
            "Expected initialized java.lang.Object on stack, but was uninitialized"
                + " java.lang.Object"));
    if (parameters.isCfRuntime()) {
      diagnostics.assertErrorMessageThatMatches(
          containsString("Could not validate stack map frames"));
    }
  }

  public static class Main {

    // The dump is generated from the following code, where we just swap the instanceof with the
    // initializer call.
    public static void main(String[] args) {
      boolean m = (new Object() instanceof Main);
    }
  }

  static class MainDump implements Opcodes {

    static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_PUBLIC | ACC_SUPER,
          "com/android/tools/r8/cf/stackmap/UninitializedInstanceOfTest$Main",
          null,
          "java/lang/Object",
          null);

      classWriter.visitInnerClass(
          "com/android/tools/r8/cf/stackmap/UninitializedInstanceOfTest$Main",
          "com/android/tools/r8/cf/stackmap/UninitializedInstanceOfTest",
          "Main",
          ACC_PUBLIC | ACC_STATIC);

      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitTypeInsn(NEW, "java/lang/Object");
        methodVisitor.visitInsn(DUP);
        // INSTANCEOF and INVOKESPECIAL is swapped.
        methodVisitor.visitTypeInsn(
            INSTANCEOF, "com/android/tools/r8/cf/stackmap/UninitializedInstanceOfTest$Main");
        methodVisitor.visitVarInsn(ISTORE, 1);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(2, 2);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
