// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import static com.android.tools.r8.ToolHelper.getMostRecentAndroidJar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.LibraryModeledPredicate;
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
public class VirtualOverrideOfStaticMethodWithVirtualParentTest extends AsmTestBase {

  public interface I {
    default void f() {}
  }

  public static class Base {
    private // Made public using ASM.
    void f() {}
  }

  public static class A extends Base {
    private // Made public using ASM.
    static void f() {}
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
      return VirtualOverrideOfStaticMethodWithVirtualParentTest.class
              .getTypeName()
              .replace('.', '/')
          + suffix;
    }

    public static byte[] dump() {
      ClassWriter cw = new ClassWriter(0);
      cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER, prefix("$Base"), null, "java/lang/Object", null);
      MethodVisitor mv;
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

  public static class ADump implements Opcodes {

    static String prefix(String suffix) {
      return VirtualOverrideOfStaticMethodWithVirtualParentTest.class
              .getTypeName()
              .replace('.', '/')
          + suffix;
    }

    public static byte[] dump() {
      ClassWriter cw = new ClassWriter(0);
      cw.visit(V1_8, ACC_PUBLIC | ACC_SUPER, prefix("$A"), null, prefix("$Base"), null);
      MethodVisitor mv;
      {
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, prefix("$Base"), "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
      }
      {
        // Changed ACC_PRIVATE to ACC_PUBLIC
        mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "f", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
      }
      cw.visitEnd();
      return cw.toByteArray();
    }
  }

  public static List<Class<?>> CLASSES = ImmutableList.of(B.class, C.class, I.class, Main.class);

  public static List<byte[]> DUMPS = ImmutableList.of(BaseDump.dump(), ADump.dump());

  private static AppView<AppInfoWithLiveness> appView;
  private static AppInfoWithLiveness appInfo;

  @BeforeClass
  public static void computeAppInfo() throws Exception {
    appView =
        computeAppViewWithLiveness(
            buildClasses(CLASSES)
                .addClassProgramData(DUMPS)
                .addLibraryFile(getMostRecentAndroidJar())
                .build(),
            Main.class);
    appInfo = appView.appInfo();
  }

  private static DexMethod buildMethod(Class clazz, String name) {
    return buildNullaryVoidMethod(clazz, name, appView.dexItemFactory());
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;
  private final DexMethod methodOnA = buildMethod(A.class, "f");
  private final DexMethod methodOnB = buildMethod(B.class, "f");
  private final DexMethod methodOnC = buildMethod(C.class, "f");

  public VirtualOverrideOfStaticMethodWithVirtualParentTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void lookupSingleTarget() {
    DexProgramClass bClass = appView.definitionForProgramType(methodOnB.holder);
    MethodResolutionResult resolutionResult =
        appInfo.resolveMethodOnClassLegacy(methodOnB.holder, methodOnB);
    DexEncodedMethod resolved = resolutionResult.getSingleTarget();
    assertEquals(methodOnA, resolved.getReference());
    assertFalse(resolutionResult.isVirtualTarget());
    DexClassAndMethod singleVirtualTarget =
        appInfo.lookupSingleVirtualTargetForTesting(
            appView,
            methodOnB,
            bClass.getProgramDefaultInitializer(),
            false,
            LibraryModeledPredicate.alwaysFalse(),
            DynamicType.unknown());
    Assert.assertNull(singleVirtualTarget);
  }

  @Test
  public void lookupVirtualTargets() {
    MethodResolutionResult resolutionResult =
        appInfo.resolveMethodOnClassLegacy(methodOnB.holder, methodOnB);
    DexEncodedMethod resolved = resolutionResult.getSingleTarget();
    assertEquals(methodOnA, resolved.getReference());
    assertFalse(resolutionResult.isVirtualTarget());
  }

  @Test
  public void testJvmAndD8() throws Exception {
    TestRunResult<?> runResult;
    if (parameters.isCfRuntime()) {
      runResult =
          testForJvm(parameters)
              .addProgramClasses(CLASSES)
              .addProgramClassFileData(DUMPS)
              .run(parameters.getRuntime(), Main.class);
    } else {
      runResult =
          testForD8()
              .addProgramClasses(CLASSES)
              .addProgramClassFileData(DUMPS)
              .setMinApi(parameters)
              .run(parameters.getRuntime(), Main.class);
    }
    checkResult(runResult, false);
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(CLASSES)
            .addProgramClassFileData(DUMPS)
            .addKeepMainRule(Main.class)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), Main.class);
    checkResult(runResult, true);
  }

  private void checkResult(TestRunResult<?> runResult, boolean isCorrectedByR8) {
    if (expectedToIncorrectlyRun(isCorrectedByR8)) {
      // Do to incorrect resolution, some Art 7 will resolve to Base.f (ignoring A.f) and thus
      // virtual dispatch to C.f. See b/140013075.
      runResult.assertSuccessWithOutputLines("Called C.f");
    } else {
      runResult.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
    }
  }

  private boolean expectedToIncorrectlyRun(boolean isCorrectedByR8) {
    return !isCorrectedByR8
        && parameters.canUseDefaultAndStaticInterfaceMethods()
        && parameters.isDexRuntimeVersion(Version.V7_0_0);
  }
}
