// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.interfacediamonds;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestAppViewBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DefaultRightAbstractLeftTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DefaultRightAbstractLeftTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  public static Collection<Class<?>> CLASSES =
      ImmutableList.of(T.class, L.class, R.class, Main.class);

  @Test
  public void testResolution() throws Exception {
    // The resolution is runtime independent, so just run it on the default CF VM.
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AppInfoWithLiveness appInfo =
        TestAppViewBuilder.builder()
            .addProgramClasses(CLASSES)
            .addProgramClassFileData(transformB())
            .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
            .addKeepMainRule(Main.class)
            .setMinApi(apiLevelWithDefaultInterfaceMethodsSupport())
            .buildWithLiveness()
            .appInfo();
    DexMethod method = buildNullaryVoidMethod(B.class, "f", appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
    DexEncodedMethod resolutionTarget = resolutionResult.getSingleTarget();
    assertEquals(R.class.getTypeName(), resolutionTarget.getHolderType().toSourceString());
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addProgramClasses(CLASSES)
        .addProgramClassFileData(transformB())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("R::f");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .addProgramClassFileData(transformB())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("R::f");
  }

  public interface T {
    void f();
  }

  public interface L extends T {
    @Override
    void f();
  }

  public interface R extends T {
    @Override
    default void f() {
      System.out.println("R::f");
    }
  }

  public static class B implements /* L via ASM */ R {
    // Intentionally empty.
  }

  static class Main {
    public static void main(String[] args) {
      new B().f();
    }
  }

  private static byte[] transformB() throws Exception {
    return transformer(B.class).setImplements(L.class, R.class).transform();
  }
}
