// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.interfacediamonds;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.resolution.SingleTargetLookupTest;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.ASMifier;

@RunWith(Parameterized.class)
public class TwoDefaultMethodsWithoutTopTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public TwoDefaultMethodsWithoutTopTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static final List<Class<?>> CLASSES =
      ImmutableList.of(I.class, J.class, A.class, Main.class);

  @Test
  public void testResolution() throws Exception {
    // The resolution is runtime independent, so just run it on the default CF VM.
    assumeTrue(parameters.getRuntime().equals(TestRuntime.getDefaultJavaRuntime()));
    AppInfoWithLiveness appInfo =
        SingleTargetLookupTest.createAppInfoWithLiveness(
            buildClasses(CLASSES, Collections.emptyList())
                .addClassProgramData(Collections.singletonList(DumpB.dump()))
                .build(),
            Main.class);
    DexMethod method = SingleTargetLookupTest.buildMethod(B.class, "f", appInfo);
    ResolutionResult resolutionResult = appInfo.resolveMethod(method.holder, method);
    Set<String> holders = new HashSet<>();
    resolutionResult
        .asFailedResolution()
        .forEachFailureDependency(
            clazz -> fail("Unexpected class dependency"),
            m -> holders.add(m.method.holder.toSourceString()));
    assertEquals(ImmutableSet.of(I.class.getTypeName(), J.class.getTypeName()), holders);
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(CLASSES)
        .addProgramClassFileData(DumpB.dump())
        .run(parameters.getRuntime(), Main.class)
        .apply(r -> checkResult(r, false));
  }

  @Test
  public void testR8() throws Exception {
    try {
      testForR8(parameters.getBackend())
          .addProgramClasses(CLASSES)
          .addProgramClassFileData(DumpB.dump())
          .addKeepMainRule(Main.class)
          .setMinApi(parameters.getApiLevel())
          .run(parameters.getRuntime(), Main.class)
          .apply(r -> checkResult(r, true));
    } catch (CompilationFailedException e) {
      // TODO(b/72208584) The desugared version of this test leads to R8 assertion errors.
      assertThat(e.getCause().getMessage(), containsString("AssertionError"));
      assertTrue(parameters.isDexRuntime());
      assertTrue(parameters.getApiLevel().isLessThan(AndroidApiLevel.N));
      return;
    }
    assertTrue(
        parameters.isCfRuntime()
            || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N));
  }

  private void checkResult(TestRunResult<?> runResult, boolean isR8) {
    // TODO(b/144085169): R8 changes exception behavior in the classfile pipeline.
    if (isR8 && parameters.isCfRuntime()) {
      runResult.assertFailureWithErrorThatMatches(containsString("NullPointerException"));
    } else if (parameters.isDexRuntime()
        && parameters.getApiLevel().isLessThan(AndroidApiLevel.N)) {
      if (isR8) {
        // TODO(b/144085169): Maybe R8 introduces another error due to removal of targets?
        runResult.assertFailureWithErrorThatMatches(containsString("AbstractMethodError"));
      } else {
        // TODO(b/72208584): Desugare changes error result.
        runResult.assertSuccessWithOutputLines("I::f");
      }
    } else {
      runResult.assertFailureWithErrorThatMatches(containsString("IncompatibleClassChangeError"));
    }
  }

  public interface I {
    default void f() {
      System.out.println("I::f");
    }
  }

  public interface J {
    default void f() {
      System.out.println("J::f");
    }
  }

  public static class A implements I {}

  public static class B extends A /* implements J via ASM */ {
    // Intentionally empty.
  }

  static class Main {
    public static void main(String[] args) {
      new B().f();
    }
  }

  private static class DumpB implements Opcodes {

    public static void main(String[] args) throws Exception {
      ASMifier.main(
          new String[] {"-debug", ToolHelper.getClassFileForTestClass(B.class).toString()});
    }

    public static byte[] dump() {

      ClassWriter classWriter = new ClassWriter(0);
      MethodVisitor methodVisitor;

      classWriter.visit(
          V1_8,
          ACC_PUBLIC | ACC_SUPER,
          DescriptorUtils.getBinaryNameFromJavaType(B.class.getTypeName()),
          null,
          DescriptorUtils.getBinaryNameFromJavaType(A.class.getTypeName()),
          new String[] {
            // Manually added 'implements J'.
            DescriptorUtils.getBinaryNameFromJavaType(J.class.getTypeName()),
          });

      {
        methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(
            INVOKESPECIAL,
            DescriptorUtils.getBinaryNameFromJavaType(A.class.getTypeName()),
            "<init>",
            "()V",
            false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
