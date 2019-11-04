// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class VirtualOverrideOfPrivateStaticMethodWithVirtualParentTest extends AsmTestBase {

  public interface I {
    default void f() {}
  }

  public static class Base {
    private // Made public using ASM.
    void f() {}
  }

  public static class A extends Base {
    private static void f() {}
  }

  public static class B extends A implements I {}

  public static class C extends B {
    public void f() {
      System.out.println("Called C.f");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      B b = new C();
      b.f();
    }
  }

  public static class BaseDump implements Opcodes {

    static String prefix(String suffix) {
      return VirtualOverrideOfPrivateStaticMethodWithVirtualParentTest.class
              .getTypeName()
              .replace('.', '/')
          + suffix;
    }

    public static byte[] dump() {
      ClassWriter cw = new ClassWriter(0);
      MethodVisitor mv;
      cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER, prefix("$Base"), null, "java/lang/Object", null);
      cw.visitSource("VirtualOverrideOfPrivateStaticMethodWithVirtualParentTest.java", null);
      {
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
      }
      {
        // Changed ACC_PRIVATE to ACC_PUBLIC
        mv = cw.visitMethod(ACC_PUBLIC, "f", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
      }
      cw.visitEnd();
      return cw.toByteArray();
    }
  }

  public static List<Class<?>> CLASSES =
      ImmutableList.of(A.class, B.class, C.class, I.class, Main.class);

  public static List<byte[]> DUMPS = ImmutableList.of(BaseDump.dump());

  private static AppInfoWithLiveness appInfo;

  @BeforeClass
  public static void computeAppInfo() throws Exception {
    appInfo =
        SingleTargetLookupTest.createAppInfoWithLiveness(
            readClassesAndAsmDump(CLASSES, DUMPS), Main.class);
  }

  private static DexMethod buildMethod(Class clazz, String name) {
    return SingleTargetLookupTest.buildMethod(clazz, name, appInfo);
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;
  private final DexMethod methodOnA = buildMethod(A.class, "f");
  private final DexMethod methodOnB = buildMethod(B.class, "f");
  private final DexMethod methodOnC = buildMethod(C.class, "f");

  public VirtualOverrideOfPrivateStaticMethodWithVirtualParentTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void lookupSingleTarget() {
    DexEncodedMethod resolved =
        appInfo.resolveMethodOnClass(methodOnB.holder, methodOnB).asResultOfResolve();
    assertEquals(methodOnA, resolved.method);
    DexEncodedMethod singleVirtualTarget =
        appInfo.lookupSingleVirtualTarget(methodOnB, methodOnB.holder);
    Assert.assertNull(singleVirtualTarget);
  }

  @Test
  public void lookupVirtualTargets() {
    ResolutionResult resolutionResult = appInfo.resolveMethodOnClass(methodOnB.holder, methodOnB);
    DexEncodedMethod resolved = resolutionResult.getSingleTarget();
    assertEquals(methodOnA, resolved.method);
    assertFalse(resolutionResult.isValidVirtualTarget(appInfo.app().options));
  }

  @Test
  public void runJvmAndD8() throws Exception {
    TestRunResult<?> runResult;
    if (parameters.isCfRuntime()) {
      runResult =
          testForJvm()
              .addProgramClasses(CLASSES)
              .addProgramClassFileData(DUMPS)
              .run(parameters.getRuntime(), Main.class);
    } else {
      runResult =
          testForD8()
              .addProgramClasses(CLASSES)
              .addProgramClassFileData(DUMPS)
              .setMinApi(parameters.getApiLevel())
              .run(parameters.getRuntime(), Main.class);
    }
    checkResult(runResult);
  }

  @Test
  public void runR8() throws Exception {
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(CLASSES)
            .addProgramClassFileData(DUMPS)
            .addKeepMainRule(Main.class)
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), Main.class);
    checkResult(runResult);
  }

  private void checkResult(TestRunResult<?> runResult) {
    if (expectedToIncorrectlyRun(parameters.getRuntime())) {
      // Do to incorrect resolution, some Art VMs will resolve to Base.f (ignoring A.f) and thus
      // virtual dispatch to C.f. See b/140013075.
      runResult.assertSuccessWithOutputLines("Called C.f");
    } else {
      runResult.assertFailureWithErrorThatMatches(containsString(expectedRuntimeError()));
    }
  }

  private boolean expectedToIncorrectlyRun(TestRuntime runtime) {
    return runtime.isDex()
        && runtime.asDex().getVm().isNewerThan(DexVm.ART_4_4_4_HOST)
        && runtime.asDex().getVm().isOlderThanOrEqual(DexVm.ART_7_0_0_HOST);
  }

  private String expectedRuntimeError() {
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_HOST)) {
      return "IncompatibleClassChangeError";
    }
    return "IllegalAccessError";
  }
}
