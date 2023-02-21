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
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DefaultTopAndLeftTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DefaultTopAndLeftTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static final List<Class<?>> CLASSES =
      ImmutableList.of(T.class, L.class, R.class, B.class, Main.class);

  @Test
  public void testResolution() throws Exception {
    // The resolution is runtime independent, so just run it on the default CF VM.
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AppInfoWithLiveness appInfo =
        TestAppViewBuilder.builder()
            .addProgramClasses(CLASSES)
            .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
            .addKeepMainRule(Main.class)
            .setMinApi(apiLevelWithDefaultInterfaceMethodsSupport())
            .buildWithLiveness()
            .appInfo();
    DexMethod method = buildNullaryVoidMethod(B.class, "f", appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
    DexEncodedMethod resolutionTarget = resolutionResult.getSingleTarget();
    assertEquals(L.class.getTypeName(), resolutionTarget.getHolderType().toSourceString());
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(CLASSES)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("L::f");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("L::f");
  }

  public interface T {
    default void f() {
      System.out.println("T::f");
    }
  }

  public interface L extends T {
    @Override
    default void f() {
      // Resolution will identify this as the only non-abstract and maximally-specific method.
      System.out.println("L::f");
    }
  }

  public interface R extends T {
    // Intentionally empty.
  }

  public static class B implements L, R {
    // Intentionally empty.
  }

  static class Main {
    public static void main(String[] args) {
      new B().f();
    }
  }
}
