// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.d8;

import static org.hamcrest.CoreMatchers.containsString;

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
public class NonNamedMemberClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public NonNamedMemberClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForD8()
        .addProgramClassFileData(Dump.dump())
        .setMinApi(parameters)
        .compile()
        .assertOnlyInfos()
        .assertInfoMessageThatMatches(containsString("missing EnclosingMethod"));
  }

  // Compiled the following kt code:
  //   class WebContext {
  //     companion object {
  //       val debug = true
  //     }
  //   }
  // and removed inner-name, Companion.
  private static class Dump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_6,
          ACC_PUBLIC | ACC_FINAL | ACC_SUPER,
          "WebContext$Companion",
          null,
          "java/lang/Object",
          null);

      classWriter.visitSource("WebContext.kt", null);

      // Removed the inner-name, Companion.
      classWriter.visitInnerClass(
          "WebContext$Companion", "WebContext", null, ACC_PUBLIC | ACC_FINAL | ACC_STATIC);

      {
        methodVisitor =
            classWriter.visitMethod(ACC_PUBLIC | ACC_FINAL, "getDebug", "()Z", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(3, label0);
        methodVisitor.visitMethodInsn(
            INVOKESTATIC, "WebContext", "access$getDebug$cp", "()Z", false);
        methodVisitor.visitInsn(IRETURN);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLocalVariable("this", "LWebContext$Companion;", null, label0, label1, 0);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(2, label0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(RETURN);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLocalVariable("this", "LWebContext$Companion;", null, label0, label1, 0);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_SYNTHETIC,
                "<init>",
                "(Lkotlin/jvm/internal/DefaultConstructorMarker;)V",
                null,
                null);
        methodVisitor.visitCode();
        Label label0 = new Label();
        methodVisitor.visitLabel(label0);
        methodVisitor.visitLineNumber(2, label0);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL, "WebContext$Companion", "<init>", "()V", false);
        methodVisitor.visitInsn(RETURN);
        Label label1 = new Label();
        methodVisitor.visitLabel(label1);
        methodVisitor.visitLocalVariable("this", "LWebContext$Companion;", null, label0, label1, 0);
        methodVisitor.visitLocalVariable(
            "$constructor_marker",
            "Lkotlin/jvm/internal/DefaultConstructorMarker;",
            null,
            label0,
            label1,
            1);
        methodVisitor.visitMaxs(1, 2);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
