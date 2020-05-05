// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class ApiLevelBackportsTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesStartingFromIncluding(Version.V9_0_0).build();
  }

  public ApiLevelBackportsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void backportSucceedsOnSupportedApiLevel() throws Exception {
    testForD8()
        .addProgramClassFileData(Dump.mainWithMathMultiplyExactLongInt())
        .setMinApi(AndroidApiLevel.B)
        .run(parameters.getRuntime(), "Test")
        .assertSuccessWithOutputLines("4");
  }

  @Test
  public void warningForNonPlatformBuild() throws Exception {
    testForD8()
        .addProgramClassFileData(Dump.mainWithMathMultiplyExactLongInt())
        .setMinApi(30)
        .compile()
        .assertOnlyWarnings()
        .assertWarningMessageThatMatches(
            containsString("An API level of 30 is not supported by this compiler"))
        .run(parameters.getRuntime(), "Test")
        .assertFailureWithErrorThatMatches(
            containsString("java.lang.NoSuchMethodError: No static method multiplyExact(JI)J"));
  }

  @Test
  public void noWarningForPlatformBuild() throws Exception {
    testForD8()
        .addProgramClassFileData(Dump.mainWithMathMultiplyExactLongInt())
        .setMinApi(AndroidApiLevel.magicApiLevelUsedByAndroidPlatformBuild)
        .run(parameters.getRuntime(), "Test")
        .assertFailureWithErrorThatMatches(
            containsString("java.lang.NoSuchMethodError: No static method multiplyExact(JI)J"));
  }

  static class Dump implements Opcodes {

    // Code for:
    //
    // class Test {
    //   public static void main(String[] args) {
    //     // Call Math.multiplyExact(long, int), which is not in Android Q.
    //     System.out.println(Math.multiplyExact(2L, 2));
    //   }
    // }
    //
    static byte[] mainWithMathMultiplyExactLongInt() {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(V1_8, ACC_SUPER, "Test", null, "java/lang/Object", null);

      classWriter.visitSource("Test.java", null);

      {
        methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(1, label0);
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
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(3, label0);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn(Long.valueOf(2L));
        methodVisitor.visitLdcInsn(Integer.valueOf(2));
        methodVisitor.visitMethodInsn(
            INVOKESTATIC, "java/lang/Math", "multiplyExact", "(JI)J", false);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(J)V", false);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(4, label1);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(5, 1);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
