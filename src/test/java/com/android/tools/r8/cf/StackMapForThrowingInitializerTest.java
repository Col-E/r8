// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** This is a reproduction of b/183285081 */
@RunWith(Parameterized.class)
public class StackMapForThrowingInitializerTest extends TestBase {

  private final TestParameters parameters;
  private final String[] EXPECTED = new String[] {"Hello World"};

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public StackMapForThrowingInitializerTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(StackMapForThrowingInitializerTest$MainDump.dump())
        .run(parameters.getRuntime(), Main.class, EXPECTED)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(StackMapForThrowingInitializerTest$MainDump.dump())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class, EXPECTED)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(StackMapForThrowingInitializerTest$MainDump.dump())
        .addKeepClassAndMembersRules(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class, EXPECTED)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @NeverClassInline
  public static class Main {

    private final String str;

    @NeverInline
    public Main(String str) {
      this.str = str;
    }

    public Main(Object obj, String str) {
      this(str);
      if (str.equals("foo")) {
        throw new IllegalArgumentException();
      }
      if (obj == null) {
        throw new RuntimeException();
      }
    }

    @Override
    public String toString() {
      return str;
    }

    public static void main(String[] args) {
      System.out.println(
          args.length == 0 ? new Main(null, "foo") : new Main(Main.class, args[0]).toString());
    }
  }

  /**
   * The dump below is just the ASM code of the class above where:
   *
   * <pre>
   *     public Main(Object obj, String str) {
   *       this(str);
   *       if (str.equals("foo")) {
   *         throw new IllegalArgumentException();
   *       }
   *       if (obj == null) {
   *         throw new RuntimeException();
   *       }
   *     }
   * </pre>
   *
   * is changed to have the initializer call in the bottom:
   *
   * <pre>
   *     public Main(Object obj, String str) {
   *       if (str.equals("foo")) {
   *         throw new IllegalArgumentException();
   *       }
   *       if (obj == null) {
   *         throw new RuntimeException();
   *       }
   *       this(str);
   *     }
   * </pre>
   */
  public static class StackMapForThrowingInitializerTest$MainDump implements Opcodes {

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      FieldVisitor fieldVisitor;
      MethodVisitor methodVisitor;
      AnnotationVisitor annotationVisitor0;

      classWriter.visit(
          V1_8,
          ACC_PUBLIC | ACC_SUPER,
          "com/android/tools/r8/cf/StackMapForThrowingInitializerTest$Main",
          null,
          "java/lang/Object",
          null);

      {
        annotationVisitor0 =
            classWriter.visitAnnotation("Lcom/android/tools/r8/NeverClassInline;", false);
        annotationVisitor0.visitEnd();
      }
      classWriter.visitInnerClass(
          "com/android/tools/r8/cf/StackMapForThrowingInitializerTest$Main",
          "com/android/tools/r8/cf/StackMapForThrowingInitializerTest",
          "Main",
          ACC_PUBLIC | ACC_STATIC);

      {
        fieldVisitor =
            classWriter.visitField(
                ACC_PRIVATE | ACC_FINAL, "str", "Ljava/lang/String;", null, null);
        fieldVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/lang/String;)V", null, null);
        {
          annotationVisitor0 =
              methodVisitor.visitAnnotation("Lcom/android/tools/r8/NeverInline;", false);
          annotationVisitor0.visitEnd();
        }
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitVarInsn(ALOAD, 0);
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
        methodVisitor.visitLdcInsn("foo");
        methodVisitor.visitMethodInsn(
            INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
        Label label0 = new Label();
        methodVisitor.visitJumpInsn(IFEQ, label0);
        methodVisitor.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "()V", false);
        methodVisitor.visitInsn(ATHROW);
        methodVisitor.visitLabel(label0);
        methodVisitor.visitFrame(
            Opcodes.F_FULL,
            3,
            new Object[] {Opcodes.UNINITIALIZED_THIS, "java/lang/Object", "java/lang/String"},
            0,
            new Object[] {});
        methodVisitor.visitVarInsn(ALOAD, 1);
        Label label1 = new Label();
        methodVisitor.visitJumpInsn(IFNONNULL, label1);
        methodVisitor.visitTypeInsn(NEW, "java/lang/RuntimeException");
        methodVisitor.visitInsn(DUP);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false);
        methodVisitor.visitInsn(ATHROW);
        methodVisitor.visitLabel(label1);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 2);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            "com/android/tools/r8/cf/StackMapForThrowingInitializerTest$Main",
            "<init>",
            "(Ljava/lang/String;)V",
            false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(2, 3);
        methodVisitor.visitEnd();
      }
      {
        methodVisitor =
            classWriter.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitFieldInsn(
            GETFIELD,
            "com/android/tools/r8/cf/StackMapForThrowingInitializerTest$Main",
            "str",
            "Ljava/lang/String;");
        methodVisitor.visitInsn(ARETURN);
        methodVisitor.visitMaxs(1, 1);
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
