// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.interfacediamonds;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.resolution.SingleTargetLookupTest;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.ASMifier;

@RunWith(Parameterized.class)
public class DefaultTopAbstractRightTest extends TestBase {

  public static Collection<Class<?>> CLASSES =
      ImmutableList.of(T.class, L.class, R.class, Main.class);

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DefaultTopAbstractRightTest(TestParameters parameters) {
    this.parameters = parameters;
  }

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
    List<DexEncodedMethod> resolutionTargets = resolutionResult.asListOfTargets();
    assertEquals(1, resolutionTargets.size());
    assertEquals(R.class.getTypeName(), resolutionTargets.get(0).method.holder.toSourceString());
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(CLASSES)
        .addProgramClassFileData(DumpB.dump())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(getExpectedErrorMatcher(false));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .addProgramClassFileData(DumpB.dump())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(getExpectedErrorMatcher(true));
  }

  private Matcher<String> getExpectedErrorMatcher(boolean isR8) {
    if (isR8
        && (parameters.isCfRuntime() || parameters.getApiLevel().isLessThan(AndroidApiLevel.L))) {
      // TODO(b/144085169): R8 replaces the entire main method by 'throw null', why?
      return containsString("NullPointerException");
    }
    if (parameters.isDexRuntime()
        && parameters
            .getRuntime()
            .asDex()
            .getVm()
            .getVersion()
            .isOlderThanOrEqual(Version.V4_4_4)) {
      return containsString("VerifyError");
    }
    return containsString("AbstractMethodError");
  }

  public interface T {
    default void f() {
      System.out.println("T::f");
    }
  }

  public interface L extends T {
    // Intentionally empty.
  }

  public interface R extends T {
    @Override
    void f(); // This causes T::f to not be maximally specific and so it must not be resolved to.
  }

  public static class B implements L /*, R via ASM */ {
    // Intentionally empty.
  }

  static class Main {
    public static void main(String[] args) {
      new B().f();
    }
  }

  static class DumpB implements Opcodes {

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
          "java/lang/Object",
          new String[] {
            DescriptorUtils.getBinaryNameFromJavaType(L.class.getTypeName()),
            // Manually added 'implements R'.
            DescriptorUtils.getBinaryNameFromJavaType(R.class.getTypeName())
          });

      {
        methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
      }
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
