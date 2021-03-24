// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** This is a reproduction of b/183285081 */
@RunWith(Parameterized.class)
public class StackMapForAlwaysThrowingInitializerWithControlFlowTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public StackMapForAlwaysThrowingInitializerWithControlFlowTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(StackMapForThrowingInitializerTest$MainDump.dump())
        .run(parameters.getRuntime(), StackMapForThrowingInitializerTest.Main.class, "")
        .assertFailureWithErrorThatThrows(VerifyError.class)
        .assertFailureWithErrorThatMatches(
            containsString("Current frame's flags are not assignable to stack map frame's"));
  }

  /**
   * The dump below checks what happens when we clobber the uninitialized this pointer and have
   * control flow:
   *
   * <pre>
   *     public Main(Object obj, String str) {
   *       this = str           <-- clobber the uninitialized this pointer
   *       if (str.equals("foo")) {
   *         throw new IllegalArgumentException();
   *       } else {
   *         throw new RuntimeException();
   *       }
   *     }
   * </pre>
   */
  public static class StackMapForThrowingInitializerTest$MainDump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      FieldVisitor fieldVisitor;
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_PUBLIC,
          "com/android/tools/r8/cf/StackMapForThrowingInitializerTest$Main",
          null,
          "java/lang/Object",
          null);

      {
        fieldVisitor =
            classWriter.visitField(
                ACC_PRIVATE | ACC_FINAL, "str", "Ljava/lang/String;", null, null);
        fieldVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitFieldInsn(
            PUTFIELD,
            "com/android/tools/r8/cf/StackMapForThrowingInitializerTest$Main",
            "str",
            "Ljava/lang/String;");
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(2, 2);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC, "<init>", "(Ljava/lang/Object;Ljava/lang/String;)V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitVarInsn(ASTORE, 0);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitLdcInsn("foo");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label label0 = new Label();
        methodVisitor.visitJumpInsn(IFNE, label0);
        methodVisitor.visitFrame(Opcodes.F_FULL, 0, new Object[] {}, 0, new Object[] {});
        methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false);
        methodVisitor.visitInsn(ATHROW);
        methodVisitor.visitLabel(label0);
        methodVisitor.visitFrame(Opcodes.F_FULL, 0, new Object[] {}, 0, new Object[] {});
        methodVisitor.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "()V", false);
        methodVisitor.visitInsn(ATHROW);
        methodVisitor.visitMaxs(2, 3);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(
                ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitInsn(ARRAYLENGTH);
        Label label0 = new Label();
        methodVisitor.visitJumpInsn(IFNE, label0);
        methodVisitor.visitTypeInsn(
            NEW, "com/android/tools/r8/cf/StackMapForThrowingInitializerTest$Main");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitInsn(ACONST_NULL);
        methodVisitor.visitLdcInsn("foo");
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/android/tools/r8/cf/StackMapForThrowingInitializerTest$Main",
            "<init>",
            "(Ljava/lang/Object;Ljava/lang/String;)V",
            false);
        Label label1 = new Label();
        methodVisitor.visitJumpInsn(GOTO, label1);
        methodVisitor.visitLabel(label0);
        methodVisitor.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/io/PrintStream"});
        methodVisitor.visitTypeInsn(
            NEW, "com/android/tools/r8/cf/StackMapForThrowingInitializerTest$Main");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitLdcInsn(
            Type.getType("Lcom/android/tools/r8/cf/StackMapForThrowingInitializerTest$Main;"));
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitInsn(AALOAD);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/android/tools/r8/cf/StackMapForThrowingInitializerTest$Main",
            "<init>",
            "(Ljava/lang/Object;Ljava/lang/String;)V",
            false);
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL,
            "com/android/tools/r8/cf/StackMapForThrowingInitializerTest$Main",
            "toString",
            "()Ljava/lang/String;",
            false);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            1,
            new Object[] {"[Ljava/lang/String;"},
            2,
            new Object[] {"java/io/PrintStream", "java/lang/Object"});
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(6, 1);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
