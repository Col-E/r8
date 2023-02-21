// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.packageprivate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.resolution.packageprivate.PackagePrivateReentryTest.C;
import com.android.tools.r8.resolution.packageprivate.a.A;
import com.android.tools.r8.resolution.packageprivate.a.A.B;
import com.android.tools.r8.resolution.packageprivate.a.D;
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
public class PackagePrivateReentryWithNarrowingTest extends TestBase {

  private final TestParameters parameters;
  private static final String[] EXPECTED = new String[] {"D.foo", "D.bar", "D.foo", "D.bar"};
  private static final String[] EXPECTED_ART = new String[] {"D.foo", "D.bar", "D.foo", "C.bar"};

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public PackagePrivateReentryWithNarrowingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(A.class, B.class, C.class, D.class, Main.class)
                .addLibraryFile(parameters.getDefaultRuntimeLibrary())
                .build(),
            Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(A.class, "bar", appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
    DexProgramClass context =
        appView.definitionForProgramType(buildType(A.class, appInfo.dexItemFactory()));
    LookupResult lookupResult = resolutionResult.lookupVirtualDispatchTargets(context, appInfo);
    assertTrue(lookupResult.isLookupResultSuccess());
    Set<String> targets = new HashSet<>();
    lookupResult.forEach(
        target -> targets.add(target.getDefinition().qualifiedName()), lambda -> fail());
    ImmutableSet<String> expected =
        ImmutableSet.of(
            D.class.getTypeName() + ".bar");
    assertEquals(expected, targets);
  }

  @Test
  public void testRuntime()
      throws ExecutionException, CompilationFailedException, IOException, NoSuchMethodException {
    TestRunResult<?> runResult =
        testForRuntime(parameters)
            .addProgramClasses(A.class, B.class, C.class, Main.class)
            .addProgramClassFileData(getDWithPackagePrivateFoo())
            .run(parameters.getRuntime(), Main.class);
    if (parameters.isCfRuntime()
        || parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_TARGET)
        || parameters.getRuntime().asDex().getVm().isNewerThanOrEqual(DexVm.ART_13_0_0_TARGET)) {
      runResult.assertSuccessWithOutputLines(EXPECTED);
    } else {
      runResult.assertSuccessWithOutputLines(EXPECTED_ART);
    }
  }

  @Test
  public void testR8()
      throws ExecutionException, CompilationFailedException, IOException, NoSuchMethodException {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, B.class, C.class, Main.class)
        .addProgramClassFileData(getDWithPackagePrivateFoo())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private byte[] getDWithPackagePrivateFoo() throws NoSuchMethodException, IOException {
    return transformer(D.class)
        .setAccessFlags(D.class.getDeclaredMethod("bar"), AccessFlags::unsetPublic)
        .transform();
  }

  public static class Main {

    public static void main(String[] args) {
      C d = (C) ((Object) new D());
      A.run(d);
      d.foo();
      d.bar();
    }
  }
}
