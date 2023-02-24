// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.stackmap;

import static com.android.tools.r8.cf.stackmap.LongStackValuesInFramesTest.LongStackValuesInFramesTest$MainDump.dump;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class LongStackValuesInFramesTest extends TestBase {

  private final String[] EXPECTED = new String[] {"52"};

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
        .addProgramClasses(Tester.class)
        .addProgramClassFileData(dump())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClasses(Tester.class)
        .addProgramClassFileData(dump())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class Tester {

    @NeverInline
    public static void test(long x, int y) {
      System.out.println(x + y);
    }
  }

  public static class Main {

    // This code will be rewritten to:
    // ldc_w 10
    // bipush 10
    // if (args.length == 0) {
    //   invoke Tester.test(JI)V
    // }
    // pop
    // pop2
    public static void main(String[] args) {
      long x = 10L;
      int y = 42;
      if (args.length == 0) {
        Tester.test(x, y);
      }
    }
  }

  public static class LongStackValuesInFramesTest$MainDump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_PUBLIC | ACC_SUPER,
          "com/android/tools/r8/cf/stackmap/LongStackValuesInFramesTest$Main",
          null,
          "java/lang/Object",
          null);
      classWriter.visitSource("LongStackValuesInFramesTest.java", null);
      classWriter.visitInnerClass(
          "com/android/tools/r8/cf/stackmap/LongStackValuesInFramesTest$Main",
          "com/android/tools/r8/cf/stackmap/LongStackValuesInFramesTest",
          "Main",
          ACC_PUBLIC | ACC_STATIC);
      classWriter.visitInnerClass(
          "com/android/tools/r8/cf/stackmap/LongStackValuesInFramesTest$Tester",
          "com/android/tools/r8/cf/stackmap/LongStackValuesInFramesTest",
          "Tester",
          ACC_PUBLIC | ACC_STATIC);

      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitLdcInsn(10L);
        methodVisitor.visitIntInsn(BIPUSH, 42);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitInsn(ARRAYLENGTH);
        Label label1 = new Label();
        methodVisitor.visitJumpInsn(IFNE, label1);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "com/android/tools/r8/cf/stackmap/LongStackValuesInFramesTest$Tester",
            "test",
            "(JI)V",
            false);
        Label label2 = new Label();
        methodVisitor.visitJumpInsn(Opcodes.GOTO, label2);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            1,
            new Object[] {"[Ljava/lang/String;"},
            2,
            new Object[] {Opcodes.LONG, Opcodes.INTEGER});
        methodVisitor.visitInsn(Opcodes.POP);
        methodVisitor.visitInsn(Opcodes.POP2);
        methodVisitor.visitLabel(label2);
        methodVisitor.visitFrame(Opcodes.F_FULL, 1, new Object[] {"[Ljava/lang/String;"}, 0, null);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(4, 3);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
