// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class MethodWithoutCodeAttributeTest extends TestBase {
  private static final String MAIN = "com.android.tools.r8.Test";

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public MethodWithoutCodeAttributeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("D8 tests.", parameters.isDexRuntime());
    try {
      testForD8()
          .addProgramClassFileData(TestDump.dump())
          .compile();
      fail("Expected to fail due to multiple annotations");
    } catch (CompilationFailedException e) {
      assertThat(
          e.getCause().getMessage(),
          containsString("Absent Code attribute in method that is not native or abstract"));
    }
  }

  @Test
  public void testJVMOutput() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(TestDump.dump())
        .run(parameters.getRuntime(), MAIN)
        .assertFailureWithErrorThatMatches(
            containsString("Absent Code attribute in method that is not native or abstract"));
  }

  static class TestDump implements Opcodes {
    public static byte[] dump() {
      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8, ACC_SUPER,
          "com/android/tools/r8/Test",
          null,
          "java/lang/Object",
          null);

      {
        methodVisitor = classWriter.visitMethod(
            ACC_PUBLIC | ACC_SYNTHETIC, "foo", "(Ljava/lang/Object;)V", null, null);
        methodVisitor.visitAnnotableParameterCount(1, false);
        // b/149808321: no code attribute
        methodVisitor.visitEnd();
      }

      {
        methodVisitor = classWriter.visitMethod(0, "main", "([Ljava/lang/String;)V", null, null);
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(42, label0);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn("Test::main");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(44, label1);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
