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
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.AndroidApiLevel;
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
        computeAppViewWithLiveness(readClassesAndAsmDump(CLASSES, DUMPS), Main.class).appInfo();
  }

  private static DexMethod buildMethod(Class clazz, String name) {
    return buildNullaryVoidMethod(clazz, name, appInfo.dexItemFactory());
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;
  private final DexMethod methodOnAReference = buildMethod(A.class, "f");
  private final DexMethod methodOnBReference = buildMethod(B.class, "f");
  private final DexMethod methodOnCReference = buildMethod(C.class, "f");

  public VirtualOverrideOfStaticMethodWithVirtualParentInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void lookupSingleTarget() {
    DexProgramClass bClass = appInfo.definitionForProgramType(methodOnBReference.holder);
    ProgramMethod methodOnB = bClass.lookupProgramMethod(methodOnBReference);
    ResolutionResult resolutionResult =
        appInfo.resolveMethodOnInterface(methodOnBReference.holder, methodOnBReference);
    DexEncodedMethod resolved = resolutionResult.getSingleTarget();
    assertEquals(methodOnBReference, resolved.method);
    assertFalse(resolutionResult.isVirtualTarget());
    DexEncodedMethod singleVirtualTarget =
        appInfo.lookupSingleVirtualTarget(methodOnBReference, methodOnB, false);
    Assert.assertNull(singleVirtualTarget);
  }

  @Test
  public void lookupVirtualTargets() {
    ResolutionResult resolutionResult =
        appInfo.resolveMethodOnInterface(methodOnBReference.holder, methodOnBReference);
    DexEncodedMethod resolved = resolutionResult.getSingleTarget();
    assertEquals(methodOnBReference, resolved.method);
    assertFalse(resolutionResult.isVirtualTarget());
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
    runR8(true);
  }

  @Test
  public void runR8NoMerging() throws Exception {
    runR8(false);
  }

  public void runR8(boolean enableVerticalClassMerging) throws Exception {
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(CLASSES)
            .addProgramClassFileData(DUMPS)
            .addKeepMainRule(Main.class)
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(o -> o.enableVerticalClassMerging = enableVerticalClassMerging)
            .run(parameters.getRuntime(), Main.class);
    if (enableVerticalClassMerging) {
      // Vertical class merging will merge B and C and change the instruction to invoke-virtual
      // causing the legacy ART runtime behavior to match the expected error.
      runResult.assertFailureWithErrorThatMatches(containsString("IncompatibleClassChangeError"));
    } else {
      checkResult(runResult);
    }
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
