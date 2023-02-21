// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.interfacetargets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SubTypeMissingOverridesTest extends TestBase {

  private static final String[] EXPECTED = new String[] {"B.foo"};

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SubTypeMissingOverridesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClassesWithTestingAnnotations(I.class, A.class, B.class, C.class, Main.class)
                .addLibraryFile(parameters.getDefaultRuntimeLibrary())
                .build(),
            Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(I.class, "foo", appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult =
        appInfo.resolveMethodOnInterfaceLegacy(method.holder, method);
    DexProgramClass context =
        appView.definitionForProgramType(buildType(Main.class, appInfo.dexItemFactory()));
    LookupResult lookupResult = resolutionResult.lookupVirtualDispatchTargets(context, appInfo);
    assertTrue(lookupResult.isLookupResultSuccess());
    assertFalse(lookupResult.asLookupResultSuccess().hasLambdaTargets());
    Set<String> targets = new HashSet<>();
    lookupResult
        .asLookupResultSuccess()
        .forEach(target -> targets.add(target.getDefinition().qualifiedName()), lambda -> fail());
    ImmutableSet<String> expected =
        ImmutableSet.of(B.class.getTypeName() + ".foo", C.class.getTypeName() + ".foo");
    assertEquals(expected, targets);
  }

  @Test
  public void testRuntime() throws IOException, CompilationFailedException, ExecutionException {
    testForRuntime(parameters)
        .addInnerClasses(SubTypeMissingOverridesTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addInnerClasses(SubTypeMissingOverridesTest.class)
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @NoVerticalClassMerging
  public interface I {

    void foo();
  }

  @NeverClassInline
  public abstract static class A implements I {}

  @NeverClassInline
  public static class B extends A {

    @Override
    public void foo() {
      System.out.println("B.foo");
    }
  }

  @NeverClassInline
  public static class C extends A {

    @Override
    public void foo() {
      System.out.println("C.foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      I i;
      if (args.length == 0) {
        i = new B();
      } else {
        i = new C();
      }
      i.foo();
    }
  }
}
