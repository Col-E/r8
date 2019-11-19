// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.methods.interfaces;

import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class StaticMethodsTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimes()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .build();
  }

  public StaticMethodsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testForRuntime() throws ExecutionException, CompilationFailedException, IOException {
    testForRuntime(parameters)
        .addProgramClasses(I.class, J.class)
        .addProgramClassFileData(StaticMethodsTest$MainForJDump.dump())
        .run(parameters.getRuntime(), MainForJ.class)
        .assertFailureWithErrorThatMatches(containsString("NoSuchMethodError"));
  }

  public interface I {
    static String foo() {
      return foo("Hello ");
    }

    static String foo(String x) {
      return x + "World!";
    }
  }

  public interface J extends I {}

  public static class ImplJ implements J {}

  public static class MainForJ {

    public static void main(String[] args) {
      System.out.println(I.foo());
    }
  }

  public static class StaticMethodsTest$MainForJDump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_PUBLIC | ACC_SUPER,
          "com/android/tools/r8/shaking/methods/interfaces/StaticMethodsTest$MainForJ",
          null,
          "java/lang/Object",
          null);

      classWriter.visitSource("StaticMethodsTest.java", null);

      classWriter.visitInnerClass(
          "com/android/tools/r8/shaking/methods/interfaces/StaticMethodsTest$MainForJ",
          "com/android/tools/r8/shaking/methods/interfaces/StaticMethodsTest",
          "MainForJ",
          ACC_PUBLIC | ACC_STATIC);

      classWriter.visitInnerClass(
          "com/android/tools/r8/shaking/methods/interfaces/StaticMethodsTest$I",
          "com/android/tools/r8/shaking/methods/interfaces/StaticMethodsTest",
          "I",
          ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_INTERFACE);

      {
        methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(70, label0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(RETURN);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLocalVariable(
            "this",
            "Lcom/android/tools/r8/shaking/methods/interfaces/StaticMethodsTest$MainForJ;",
            null,
            label0,
            label1,
            0);
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
        methodVisitor.visitLineNumber(73, label0);
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitMethodInsn(
            INVOKESTATIC,
            "com/android/tools/r8/shaking/methods/interfaces/StaticMethodsTest$J",
            "foo",
            "()Ljava/lang/String;",
            true);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLineNumber(74, label1);
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
