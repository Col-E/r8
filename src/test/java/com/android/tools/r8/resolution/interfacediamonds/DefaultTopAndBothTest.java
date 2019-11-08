// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.interfacediamonds;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
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
public class DefaultTopAndBothTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DefaultTopAndBothTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  public static List<Class<?>> CLASSES = ImmutableList.of(T.class, L.class, R.class, Main.class);

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
    assertEquals(2, resolutionTargets.size());
    assertTrue(
        resolutionTargets.stream()
            .anyMatch(m -> m.method.holder.toSourceString().equals(L.class.getTypeName())));
    assertTrue(
        resolutionTargets.stream()
            .anyMatch(m -> m.method.holder.toSourceString().equals(R.class.getTypeName())));
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(CLASSES)
        .addProgramClassFileData(DumpB.dump())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(getExpectedError(false));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .addProgramClassFileData(DumpB.dump())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(getExpectedError(true));
  }

  private boolean isDesugaringDefaultInterfaceMethods() {
    return parameters.getApiLevel() != null
        && parameters.getApiLevel().getLevel() < AndroidApiLevel.N.getLevel();
  }

  private Matcher<String> getExpectedError(boolean isR8) {
    // TODO(b/144085169): JDK 11 execution produces a different error condition on the R8 output?
    if (isR8
        && parameters.getRuntime().isCf()
        && parameters.getRuntime().asCf().getVm() == CfVm.JDK11) {
      return containsString("AbstractMethodError");
    }
    // TODO(b/72208584): Default interface method desugaring changes error behavior.
    if (isDesugaringDefaultInterfaceMethods()) {
      if (isR8) {
        // TODO(b/144085169): Maybe R8 introduces another error due to removal of targets?
        return containsString("AbstractMethodError");
      }
      // Dalvik fails with a verify error instead of the runtime failure (unless R8 removed the
      // methods as indicated by the above.
      if (parameters.getRuntime().asDex().getVm().getVersion().isOlderThanOrEqual(Version.V4_4_4)) {
        return containsString("VerifyError");
      }
      return containsString("AbstractMethodError");
    }
    // Reference result should be an incompatible class change error due to the two non-abstract
    // methods in the maximally specific set.
    return containsString("IncompatibleClassChangeError");
  }

  public interface T {
    default void f() {
      System.out.println("T::f");
    }
  }

  public interface L extends T {
    // Both L::f and R::f are maximally specific, thus none will be chosen for resolution.
    @Override
    default void f() {
      System.out.println("L::f");
    }
  }

  public interface R extends T {
    // Both L::f and R::f are maximally specific, thus none will be chosen for resolution.
    @Override
    default void f() {
      System.out.println("R::f");
    }
  }

  public static class B implements L /*, R via ASM. */ {
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
