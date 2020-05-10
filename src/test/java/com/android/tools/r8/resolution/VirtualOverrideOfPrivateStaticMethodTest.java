// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.graph.ResolutionResult.IllegalAccessOrNoSuchMethodResult;
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
    default void f() {}
  }

  public static class A {
    private static void f() {}
  }

  public static class B extends A implements I {}

  public static class C extends B {
    public void f() {}
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
    appInfo = computeAppViewWithLiveness(readClasses(CLASSES), Main.class).appInfo();
  }

  private static DexMethod buildMethod(Class clazz, String name) {
    return buildNullaryVoidMethod(clazz, name, appInfo.dexItemFactory());
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
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
    ResolutionResult resolutionResult = appInfo.resolveMethodOnClass(methodOnB, methodOnB.holder);
    assertTrue(resolutionResult instanceof IllegalAccessOrNoSuchMethodResult);
  }

  @Test
  public void runTest() throws ExecutionException, CompilationFailedException, IOException {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addProgramClasses(CLASSES)
          .run(parameters.getRuntime(), Main.class)
          .assertFailureWithErrorThatMatches(containsString(expectedRuntimeError()));
    }
    testForR8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(containsString(expectedRuntimeError()));
  }

  private String expectedRuntimeError() {
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_HOST)) {
      return "IncompatibleClassChangeError";
    }
    return "IllegalAccessError";
  }
}
