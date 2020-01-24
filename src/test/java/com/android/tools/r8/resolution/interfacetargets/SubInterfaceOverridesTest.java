// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.interfacetargets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ResolutionResult;
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
public class SubInterfaceOverridesTest extends TestBase {

  private static final String[] EXPECTED = new String[] {"A.foo"};

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SubInterfaceOverridesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.useRuntimeAsNoneRuntime());
    AppInfoWithLiveness appInfo =
        computeAppViewWithLiveness(
                buildClasses(I.class, J.class, A.class, B.class, C.class, Main.class).build(),
                Main.class)
            .appInfo();
    DexMethod method = buildNullaryVoidMethod(I.class, "foo", appInfo.dexItemFactory());
    ResolutionResult resolutionResult = appInfo.resolveMethodOnInterface(method.holder, method);
    Set<String> targets =
        resolutionResult.lookupInterfaceTargets(appInfo.withSubtyping()).stream()
            .map(DexEncodedMethod::qualifiedName)
            .collect(Collectors.toSet());
    ImmutableSet<String> expected =
        ImmutableSet.of(
            J.class.getTypeName() + ".foo",
            A.class.getTypeName() + ".foo",
            C.class.getTypeName() + ".foo");
    assertEquals(expected, targets);
  }

  @Test
  public void testRuntime() throws IOException, CompilationFailedException, ExecutionException {
    testForRuntime(parameters)
        .addInnerClasses(SubInterfaceOverridesTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addInnerClasses(SubInterfaceOverridesTest.class)
        .enableMergeAnnotations()
        .enableNeverClassInliningAnnotations()
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .compile()
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
    default void foo() {
      System.out.println("J.foo");
    }
  }

  @NeverClassInline
  public static class A implements I {

    @Override
    public void foo() {
      System.out.println("A.foo");
      ;
    }
  }

  @NeverClassInline
  public static class B implements J {}

  @NeverClassInline
  public static class C implements J {

    @Override
    public void foo() {
      System.out.println("C.foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      I i;
      if (args.length == 0) {
        i = new A();
      } else {
        i = new B();
      }
      i.foo();
    }
  }
}
