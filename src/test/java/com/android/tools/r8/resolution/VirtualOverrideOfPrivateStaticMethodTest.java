// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import static com.android.tools.r8.ToolHelper.getMostRecentAndroidJar;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VirtualOverrideOfPrivateStaticMethodTest extends TestBase {

  public interface I {
    default void f() {
      System.out.println("I::f");
    }
  }

  public static class A {
    private static void f() {
      System.out.println("A::f");
    }
  }

  public static class B extends A implements I {}

  public static class C extends B {
    @Override
    public void f() {
      System.out.println("C::f");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      B b = new C();
      b.f();
    }
  }

  public static List<Class<?>> CLASSES =
      ImmutableList.of(A.class, B.class, C.class, I.class, Main.class);

  private static AppInfoWithLiveness appInfo;

  @BeforeClass
  public static void computeAppInfo() throws Exception {
    appInfo =
        computeAppViewWithLiveness(
                buildClasses(CLASSES).addLibraryFile(getMostRecentAndroidJar()).build(), Main.class)
            .appInfo();
  }

  private static DexMethod buildMethod(Class<?> clazz, String name) {
    return buildNullaryVoidMethod(clazz, name, appInfo.dexItemFactory());
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private final TestParameters parameters;
  private final DexMethod methodOnA = buildMethod(A.class, "f");
  private final DexMethod methodOnB = buildMethod(B.class, "f");
  private final DexMethod methodOnC = buildMethod(C.class, "f");

  public VirtualOverrideOfPrivateStaticMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void resolveTarget() {
    MethodResolutionResult resolutionResult =
        appInfo.resolveMethodOnClassLegacy(methodOnB.holder, methodOnB);
    DexClass context = appInfo.definitionFor(methodOnB.holder);
    assertTrue(resolutionResult.isIllegalAccessErrorResult(context, appInfo));
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(CLASSES)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  @Test
  public void testD8() throws ExecutionException, CompilationFailedException, IOException {
    testForD8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/182335909): Ideally, this should IllegalAccessError.
        .applyIf(
            parameters.canUseDefaultAndStaticInterfaceMethodsWhenDesugaring()
                && parameters.isCfRuntime(),
            r -> r.assertFailureWithErrorThatThrows(IllegalAccessError.class),
            r -> r.assertSuccessWithOutputLines("C::f"));
  }

  @Test
  public void testR8() throws ExecutionException, CompilationFailedException, IOException {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(expectedRuntimeError());
  }

  private Class<? extends Throwable> expectedRuntimeError() {
    return IllegalAccessError.class;
  }
}
