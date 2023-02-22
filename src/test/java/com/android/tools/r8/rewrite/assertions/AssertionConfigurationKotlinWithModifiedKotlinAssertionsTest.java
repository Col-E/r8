// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;

import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class AssertionConfigurationKotlinWithModifiedKotlinAssertionsTest
    extends AssertionConfigurationKotlinTestBase implements Opcodes {

  @Parameterized.Parameters(name = "{0}, {1}, kotlin-stdlib as library: {2}, -Xassertions=jvm: {3}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimesAndAllApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  public AssertionConfigurationKotlinWithModifiedKotlinAssertionsTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean kotlinStdlibAsClasspath,
      boolean useJvmAssertions) {
    super(parameters, kotlinParameters, kotlinStdlibAsClasspath, useJvmAssertions);
  }

  @Test
  public void testPassthroughAllAssertions() throws Exception {
    testForD8()
        .addProgramClassFileData(dumpModifiedKotlinAssertions())
        .addProgramFiles(compiledForAssertions.getForConfiguration(kotlinc, targetVersion))
        .setMinApi(parameters)
        .addAssertionsConfiguration(AssertionsConfiguration.Builder::passthroughAllAssertions)
        .run(
            parameters.getRuntime(),
            getClass().getPackage().getName() + ".kotlintestclasses.TestClassKt")
        .assertSuccessWithOutputLines(noAllAssertionsExpectedLines());
  }

  @Test
  public void testCompileTimeEnableAllAssertions() throws Exception {
    testForD8()
        .addProgramClassFileData(dumpModifiedKotlinAssertions())
        .addProgramFiles(compiledForAssertions.getForConfiguration(kotlinc, targetVersion))
        .setMinApi(parameters)
        .addAssertionsConfiguration(AssertionsConfiguration.Builder::compileTimeEnableAllAssertions)
        .run(
            parameters.getRuntime(),
            getClass().getPackage().getName() + ".kotlintestclasses.TestClassKt")
        .assertSuccessWithOutputLines(allAssertionsExpectedLines());
  }

  @Test
  public void testCompileTimeDisableAllAssertions() throws Exception {
    testForD8()
        .addProgramClassFileData(dumpModifiedKotlinAssertions())
        .addProgramFiles(compiledForAssertions.getForConfiguration(kotlinc, targetVersion))
        .setMinApi(parameters)
        .addAssertionsConfiguration(
            AssertionsConfiguration.Builder::compileTimeDisableAllAssertions)
        .run(
            parameters.getRuntime(),
            getClass().getPackage().getName() + ".kotlintestclasses.TestClassKt")
        .assertSuccessWithOutputLines(noAllAssertionsExpectedLines());
  }

  // Slightly modified version of kotlin._Assertions to hit all code paths in the assertion
  // rewriter. See "Code added" below.
  public static byte[] dumpModifiedKotlinAssertions() {

    ClassWriter classWriter = new ClassWriter(0);
    FieldVisitor fieldVisitor;
    MethodVisitor methodVisitor;

    classWriter.visit(
        V1_6,
        ACC_PUBLIC | ACC_FINAL | ACC_SUPER,
        "kotlin/_Assertions",
        null,
        "java/lang/Object",
        null);

    {
      fieldVisitor =
          classWriter.visitField(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "ENABLED", "Z", null, null);
      fieldVisitor.visitEnd();
    }
    {
      fieldVisitor =
          classWriter.visitField(
              ACC_PUBLIC | ACC_FINAL | ACC_STATIC, "INSTANCE", "Lkotlin/_Assertions;", null, null);
      fieldVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC | ACC_DEPRECATED,
              "ENABLED$annotations",
              "()V",
              null,
              null);
      methodVisitor.visitCode();
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(0, 0);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor = classWriter.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitTypeInsn(NEW, "kotlin/_Assertions");
      methodVisitor.visitInsn(DUP);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "kotlin/_Assertions", "<init>", "()V", false);
      methodVisitor.visitVarInsn(ASTORE, 0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitFieldInsn(
          PUTSTATIC, "kotlin/_Assertions", "INSTANCE", "Lkotlin/_Assertions;");

      // Code added (added an additional call to getClass().desiredAssertionStatus() which
      // result is not assigned to ENABLED).
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Class", "desiredAssertionStatus", "()Z", false);
      methodVisitor.visitInsn(POP);
      // End code added.

      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/lang/Class", "desiredAssertionStatus", "()Z", false);
      methodVisitor.visitFieldInsn(PUTSTATIC, "kotlin/_Assertions", "ENABLED", "Z");
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(2, 1);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
