// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.packageprivate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.resolution.packageprivate.a.A;
import com.android.tools.r8.resolution.packageprivate.a.A.B;
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
public class WidenAccessOutsidePackageTest extends TestBase {

  private final TestParameters parameters;
  private static final String[] EXPECTED = new String[] {"C.foo", "B.bar", "C.foo", "C.bar"};
  private static final String[] EXPECTED_DALVIK = new String[] {"C.foo", "C.bar", "C.foo", "C.bar"};

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public WidenAccessOutsidePackageTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(A.class, B.class, C.class, Main.class)
                .addLibraryFile(parameters.getDefaultRuntimeLibrary())
                .build(),
            Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(A.class, "bar", appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
    DexProgramClass context =
        appView.definitionForProgramType(buildType(A.class, appInfo.dexItemFactory()));
    LookupResult lookupResult = resolutionResult.lookupVirtualDispatchTargets(context, appInfo);
    assertFalse(lookupResult.asLookupResultSuccess().hasLambdaTargets());
    Set<String> targets = new HashSet<>();
    lookupResult
        .asLookupResultSuccess()
        .forEach(
            target -> targets.add(target.getDefinition().qualifiedName()),
            lambda -> {
              fail();
            });
    ImmutableSet<String> expected = ImmutableSet.of(B.class.getTypeName() + ".bar");
    assertEquals(expected, targets);
  }

  @Test
  public void testRuntime() throws ExecutionException, CompilationFailedException, IOException {
    TestRunResult<?> runResult =
        testForRuntime(parameters)
            .addProgramClasses(A.class, B.class, C.class, Main.class)
            .run(parameters.getRuntime(), Main.class);
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_TARGET)) {
      runResult.assertSuccessWithOutputLines(EXPECTED_DALVIK);
    } else {
      runResult.assertSuccessWithOutputLines(EXPECTED);
    }
  }

  @Test
  public void testR8() throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, B.class, C.class, Main.class)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class C extends B {
    @Override
    public void foo() {
      System.out.println("C.foo");
    }

    public void bar() {
      System.out.println("C.bar");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      C c = new C();
      A.run(c);
      c.foo();
      c.bar();
    }
  }
}
