// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.stackmap;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.cf.stackmap.UninitializedPutFieldTest.MainDump.dump;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
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
public class UninitializedPutFieldTest extends TestBase {

  private static final String[] EXPECTED = new String[] {"Main::foo"};

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
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testD8Cf() throws Exception {
    parameters.assumeCfRuntime();
    testForD8(parameters.getBackend())
        .addProgramClassFileData(dump())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertNoWarningsMatch(
                  diagnosticMessage(containsString("The expected type uninitialized")));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testD8Dex() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClassFileData(dump())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertNoWarningsMatch(
                  diagnosticMessage(containsString("The expected type uninitialized")));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class Main {

    private Object object;

    private Main() {
      this.object = new Object();
      foo(this.object);
    }

    private void foo(Object object) {
      System.out.println("Main::foo");
    }

    public static void main(String[] args) {
      new Main();
    }
  }

  // The dump is generated from the above code. The change made is to Main::<init> where we
  // now putfield before initializing this.
  static class MainDump implements Opcodes {

    static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      FieldVisitor fieldVisitor;
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_PUBLIC | ACC_SUPER,
          "com/android/tools/r8/cf/stackmap/UninitializedPutFieldTest$Main",
          null,
          "java/lang/Object",
          null);

      classWriter.visitInnerClass(
          "com/android/tools/r8/cf/stackmap/UninitializedPutFieldTest$Main",
          "com/android/tools/r8/cf/stackmap/UninitializedPutFieldTest",
          "Main",
          ACC_PUBLIC | ACC_STATIC);

      {
        fieldVisitor =
            classWriter.visitField(ACC_PRIVATE, "object", "Ljava/lang/Object;", null, null);
        fieldVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitTypeInsn(NEW, "java/lang/Object");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitFieldInsn(
            PUTFIELD,
            "com/android/tools/r8/cf/stackmap/UninitializedPutFieldTest$Main",
            "object",
            "Ljava/lang/Object;");
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "com/android/tools/r8/cf/stackmap/UninitializedPutFieldTest$Main",
            "object",
            "Ljava/lang/Object;");
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/android/tools/r8/cf/stackmap/UninitializedPutFieldTest$Main",
            "foo",
            "(Ljava/lang/Object;)V",
            false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(3, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(ACC_PRIVATE, "foo", "(Ljava/lang/Object;)V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("Main::foo");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(2, 2);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitTypeInsn(
            NEW, "com/android/tools/r8/cf/stackmap/UninitializedPutFieldTest$Main");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/android/tools/r8/cf/stackmap/UninitializedPutFieldTest$Main",
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
