// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.graph.AppInfo.ResolutionResult;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
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
public class VirtualOverrideOfStaticMethodWithVirtualParentInterfaceTest extends AsmTestBase {

  public interface A {
    default void f() {}
  }

  public interface B extends A {
    // Made static using ASM.
    /*static*/ default void f() {}
  }

  public static class C implements B {
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

  public static class BDump implements Opcodes {

    static String prefix(String suffix) {
      return VirtualOverrideOfStaticMethodWithVirtualParentInterfaceTest.class
              .getTypeName()
              .replace('.', '/')
          + suffix;
    }

    public static byte[] dump() {
      ClassWriter cw = new ClassWriter(0);
      cw.visit(
          V1_8,
          ACC_PUBLIC | ACC_ABSTRACT | ACC_INTERFACE,
          prefix("$B"),
          null,
          "java/lang/Object",
          null);
      {
        // Added ACC_STATIC
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, "f", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
      }
      cw.visitEnd();
      return cw.toByteArray();
    }
  }

  public static List<Class<?>> CLASSES = ImmutableList.of(A.class, C.class, Main.class);

  public static List<byte[]> DUMPS = ImmutableList.of(BDump.dump());

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

  public VirtualOverrideOfStaticMethodWithVirtualParentInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void lookupSingleTarget() {
    DexEncodedMethod resolved =
        appInfo.resolveMethod(methodOnB.holder, methodOnB).asResultOfResolve();
    assertEquals(methodOnB, resolved.method);
    DexEncodedMethod singleVirtualTarget =
        appInfo.lookupSingleInterfaceTarget(methodOnB, methodOnB.holder);
    // TODO(b/140088797): This should not conclude a single target.
    Assert.assertNotNull(singleVirtualTarget);
  }

  @Test
  public void lookupVirtualTargets() {
    ResolutionResult resolutionResult = appInfo.resolveMethodOnInterface(methodOnB.holder, methodOnB);
    DexEncodedMethod resolved = resolutionResult.asResultOfResolve();
    assertEquals(methodOnB, resolved.method);
    Set<DexEncodedMethod> targets = resolutionResult.lookupInterfaceTargets(appInfo);
    // TODO(b/140088797): This should not conclude a single target.
    assertTrue("Expected " + methodOnC, targets.stream().anyMatch(m -> m.method == methodOnC));
    assertEquals(1, targets.size());
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
            // TODO(b/140088797): Once fixed, parameterize on the merger as it appears to fail.
            .addOptionsModification(o -> o.enableVerticalClassMerging = false)
            .run(parameters.getRuntime(), Main.class);
    // TODO(b/140088797): Due to the single target lookup R8 incorrectly inlines the call C.f().
    runResult.assertSuccessWithOutputLines("Called C.f");
  }

  private void checkResult(TestRunResult<?> runResult) {
    runResult.assertFailureWithErrorThatMatches(containsString(expectedRuntimeError()));
  }

  private String expectedRuntimeError() {
    if (parameters.isDexRuntime()
        && parameters.getApiLevel().getLevel() < AndroidApiLevel.N.getLevel()) {
      // When desugaring default interface methods the error will be NoSuchMethodError.
      return "NoSuchMethodError";
    }
    return "IncompatibleClassChangeError";
  }
}
