// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.interfacediamonds;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.resolution.SingleTargetLookupTest;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TwoDefaultMethodsWithoutTopTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public TwoDefaultMethodsWithoutTopTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static final List<Class<?>> CLASSES =
      ImmutableList.of(I.class, J.class, A.class, Main.class);

  @Test
  public void testResolution() throws Exception {
    // The resolution is runtime independent, so just run it on the default CF VM.
    assumeTrue(parameters.getRuntime().equals(TestRuntime.getDefaultJavaRuntime()));
    AppInfoWithLiveness appInfo =
        SingleTargetLookupTest.createAppInfoWithLiveness(
            buildClasses(CLASSES, Collections.emptyList())
                .addClassProgramData(Collections.singletonList(transformB()))
                .build(),
            Main.class);
    DexMethod method = SingleTargetLookupTest.buildMethod(B.class, "f", appInfo);
    ResolutionResult resolutionResult = appInfo.resolveMethod(method.holder, method);
    Set<String> holders = new HashSet<>();
    resolutionResult
        .asFailedResolution()
        .forEachFailureDependency(
            clazz -> fail("Unexpected class dependency"),
            m -> holders.add(m.method.holder.toSourceString()));
    assertEquals(ImmutableSet.of(I.class.getTypeName(), J.class.getTypeName()), holders);
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(CLASSES)
        .addProgramClassFileData(transformB())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(containsString("IncompatibleClassChangeError"));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .addProgramClassFileData(transformB())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .apply(r -> checkResultR8(r));
  }

  private void checkResultR8(TestRunResult<?> runResult) {
    // TODO(b/144085169): R8/CF produces incorrect result.
    if (parameters.getRuntime().isCf()) {
      runResult.assertFailureWithErrorThatMatches(containsString("NullPointerException"));
    } else {
      runResult.assertFailureWithErrorThatMatches(containsString("IncompatibleClassChangeError"));
    }
  }

  public interface I {
    default void f() {
      System.out.println("I::f");
    }
  }

  public interface J {
    default void f() {
      System.out.println("J::f");
    }
  }

  public static class A implements I {}

  public static class B extends A /* implements J via ASM */ {
    // Intentionally empty.
  }

  static class Main {
    public static void main(String[] args) {
      new B().f();
    }
  }

  private static byte[] transformB() throws Exception {
    return transformer(B.class).setImplements(J.class).transform();
  }
}
