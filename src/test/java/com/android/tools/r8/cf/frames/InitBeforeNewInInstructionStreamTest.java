// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.frames;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import javassist.ByteArrayClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.StackMapTable;
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
public class InitBeforeNewInInstructionStreamTest extends TestBase implements Opcodes {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public static final String MAIN_CLASS = "Test";
  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world!");

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(dump())
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addProgramClassFileData(dump())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(dump())
        .addKeepMainRule(MAIN_CLASS)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void checkDump() throws Exception {
    parameters.assumeJvmTestParameters();
    ClassPool classPool = new ClassPool();
    classPool.insertClassPath(new ByteArrayClassPath(MAIN_CLASS, dump()));
    CtClass clazz = classPool.get(MAIN_CLASS);
    clazz.defrost();
    CodeAttribute code =
        (CodeAttribute) clazz.getClassFile().getMethod("main").getAttribute("Code");
    StackMapTable stackMapTableAttribute = (StackMapTable) code.getAttribute("StackMapTable");
    byte[] stackMapTable = stackMapTableAttribute.get();
    // Uninitialized has type 8 in the stack map. See
    // https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.4.
    // Keep to validate https://gitlab.ow2.org/asm/asm/-/issues/317995 is fixed.
    assertTrue(
        stackMapTable[15] == 8
            && stackMapTable[16] == 0
            && stackMapTable[17] == 12
            && stackMapTable[18] == 8
            && stackMapTable[19] == 0
            && stackMapTable[20] == 12);
  }

  // This is reproducing b/274337639, where a new instruction is before the corresponding
  // invokespecial of <init> in the instruction stream. The code is correct as control flow ensures
  // new is called before init, and the stack map encodes this.
  public static byte[] dump() throws Exception {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(V1_8, ACC_FINAL | ACC_SUPER, MAIN_CLASS, null, "java/lang/Object", null);

    {
      methodVisitor = classWriter.visitMethod(0, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      Label labelNew = new Label();
      Label labelInit = new Label();
      Label labelAfterInit = new Label();

      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitJumpInsn(GOTO, labelNew);

      methodVisitor.visitLabel(labelInit);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          1,
          new Object[] {"[Ljava/lang/String;"},
          3,
          new Object[] {"java/io/PrintStream", labelNew, labelNew});
      methodVisitor.visitMethodInsn(INVOKESPECIAL, MAIN_CLASS, "<init>", "()V", false);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          1,
          new Object[] {"[Ljava/lang/String;"},
          2,
          new Object[] {"java/io/PrintStream", MAIN_CLASS});
      methodVisitor.visitJumpInsn(GOTO, labelAfterInit);

      methodVisitor.visitLabel(labelNew);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          1,
          new Object[] {"[Ljava/lang/String;"},
          1,
          new Object[] {"java/io/PrintStream"});
      methodVisitor.visitTypeInsn(NEW, MAIN_CLASS);
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitJumpInsn(GOTO, labelInit);

      methodVisitor.visitLabel(labelAfterInit);
      methodVisitor.visitFrame(
          Opcodes.F_FULL,
          1,
          new Object[] {"[Ljava/lang/String;"},
          2,
          new Object[] {"java/io/PrintStream", MAIN_CLASS});
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(3, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitLdcInsn("Hello, world!");
      methodVisitor.visitInsn(ARETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
