// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.interfacediamonds;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AbstractAllTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public AbstractAllTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static final List<Class<?>> CLASSES =
      ImmutableList.of(T.class, L.class, R.class, B.class, C.class, Main.class);

  @Test
  public void testResolution() throws Exception {
    // The resolution is runtime independent, so just run it on the default CF VM.
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AndroidApp app =
        buildClasses(CLASSES).addLibraryFile(parameters.getDefaultRuntimeLibrary()).build();
    AppInfoWithLiveness appInfo = computeAppViewWithLiveness(app, Main.class).appInfo();
    DexMethod method = buildNullaryVoidMethod(B.class, "f", appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
    DexEncodedMethod resolutionTarget = resolutionResult.getSingleTarget();
    // Currently R8 will resolve to L::f as that is the first in the topological search.
    // Resolution may return any of the matches, so it is valid if this expectation changes.
    assertEquals(L.class.getTypeName(), resolutionTarget.getHolderType().toSourceString());
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(AbstractAllTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("C::f");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(AbstractAllTest.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("C::f");
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
    void f();
  }

  public abstract static class B implements L, R {
    // Intentionally empty.
    // Resolving B::f can give any one of T::f, L::f or R::f.
  }

  public static class C extends B {

    @Override
    public void f() {
      System.out.println("C::f");
    }
  }

  static class Main {
    public static void main(String[] args) {
      B b = args.length == 42 ? null : new C();
      b.f();
    }
  }
}
