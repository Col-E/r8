// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.virtualtargets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DefaultInterfaceMethodInSubInterfaceSubTypeTest extends TestBase {

  private static final String EXPECTED = "J.foo";

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DefaultInterfaceMethodInSubInterfaceSubTypeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.useRuntimeAsNoneRuntime());
    AppInfoWithLiveness appInfo =
        computeAppViewWithLiveness(
                buildClasses(I.class, J.class, A.class, B.class, Main.class).build(), Main.class)
            .appInfo();
    DexMethod method = buildNullaryVoidMethod(A.class, "foo", appInfo.dexItemFactory());
    Set<String> targets =
        appInfo.resolveMethod(method.holder, method).lookupVirtualTargets(appInfo).stream()
            .map(DexEncodedMethod::qualifiedName)
            .collect(Collectors.toSet());
    // TODO(b/148591377): I.foo() should ideally not be included in the set.
    ImmutableSet<String> expected =
        ImmutableSet.of(I.class.getTypeName() + ".foo", J.class.getTypeName() + ".foo");
    assertEquals(expected, targets);
  }

  @Test
  public void testRuntime() throws IOException, CompilationFailedException, ExecutionException {
    testForRuntime(parameters)
        .addInnerClasses(DefaultInterfaceMethodInSubInterfaceSubTypeTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addInnerClasses(DefaultInterfaceMethodInSubInterfaceSubTypeTest.class)
        .addKeepMainRule(Main.class)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .enableMergeAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @NeverMerge
  public interface I {
    void foo();
  }

  @NeverMerge
  public interface J extends I {
    @Override
    @NeverInline
    default void foo() {
      System.out.println("J.foo");
    }
  }

  @NeverMerge
  public abstract static class A implements I {}

  @NeverClassInline
  public static class B extends A implements J {}

  public static class Main {

    public static void main(String[] args) {
      ((A) new B()).foo();
    }
  }
}
