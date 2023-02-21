// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.cf.CfVersion;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class SupportedClassFileVersions extends TestBase implements Opcodes {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public CfVersion version;

  @Parameters(name = "{0}, CF version = {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(), CfVersion.all());
  }

  @Test
  public void testDesugar() throws Exception {
    testForDesugaring(parameters)
        .addProgramClassFileData(dump(version))
        .run(parameters.getRuntime(), "Test")
        .applyIf(
            c ->
                DesugarTestConfiguration.isNotDesugared(c) // This implies CF runtime.
                    && version.major() > parameters.asCfRuntime().getVm().getClassfileVersion(),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class),
            r -> r.assertSuccessWithOutputLines("Hello, world!"));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(dump(version))
        .addKeepMainRule("Test")
        .setMinApi(parameters)
        .run(parameters.getRuntime(), "Test")
        .applyIf(
            parameters.isCfRuntime()
                && version.major() > parameters.asCfRuntime().getVm().getClassfileVersion(),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class),
            r -> r.assertSuccessWithOutputLines("Hello, world!"));
  }

  public static byte[] dump(CfVersion version) {
    // Generate a class file with a version higher than the supported one.
    ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    MethodVisitor methodVisitor;
    classWriter.visit(
        version.raw(), ACC_PUBLIC + ACC_SUPER, "Test", null, "java/lang/Object", null);
    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      methodVisitor.visitCode();
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
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn("Hello, world!");
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();
    return classWriter.toByteArray();
  }
}
