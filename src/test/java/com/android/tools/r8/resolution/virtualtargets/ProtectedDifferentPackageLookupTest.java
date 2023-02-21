// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.virtualtargets;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProtectedDifferentPackageLookupTest extends TestBase {

  private static final String NEW_DESCRIPTOR_FOR_B = "Lanotherpackage/B;";
  private static final String[] EXPECTED = new String[] {"A::foo", "A::foo"};

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ProtectedDifferentPackageLookupTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AndroidApp.Builder builder = AndroidApp.builder();
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(A.class));
    builder.addClassProgramData(
        ImmutableList.of(getBInAnotherPackage(), getMainWithCallToRelocatedB()));
    builder.addLibraryFile(parameters.getDefaultRuntimeLibrary());
    AppView<AppInfoWithLiveness> appView = computeAppViewWithLiveness(builder.build(), Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(A.class, "foo", appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
    DexProgramClass context =
        appView.definitionForProgramType(buildType(Main.class, appInfo.dexItemFactory()));
    LookupResult lookupResult = resolutionResult.lookupVirtualDispatchTargets(context, appInfo);
    // TODO(b/173363527): Should be an error.
    assertTrue(lookupResult.isLookupResultSuccess());
  }

  @Test
  public void testRuntime() throws Exception {
    TestRunResult<?> runResult =
        testForRuntime(parameters)
            .addProgramClasses(A.class)
            .addProgramClassFileData(getBInAnotherPackage(), getMainWithCallToRelocatedB())
            .run(parameters.getRuntime(), Main.class);
    if (parameters.isDexRuntime()) {
      // TODO(b/173363527): Figure out if this should be an error on DEX
      runResult.assertSuccessWithOutputLines(EXPECTED);
    } else {
      runResult.assertFailureWithErrorThatThrows(VerifyError.class);
    }
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class)
        .addProgramClassFileData(getBInAnotherPackage(), getMainWithCallToRelocatedB())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/173363527): Should be an error on CF.
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private byte[] getBInAnotherPackage() throws Exception {
    return transformer(B.class).setClassDescriptor(NEW_DESCRIPTOR_FOR_B).transform();
  }

  private byte[] getMainWithCallToRelocatedB() throws Exception {
    return transformer(Main.class)
        .replaceClassDescriptorInMethodInstructions(
            DescriptorUtils.javaTypeToDescriptor(B.class.getTypeName()), NEW_DESCRIPTOR_FOR_B)
        .transform();
  }

  public static class A {

    protected void foo() {
      System.out.println("A::foo");
    }
  }

  // Will be moved to another package
  public static class B extends A {

    // Invoke-super is also legal but if we add a call to this we will not inline and we
    // maintain the error.
    // public void invokeSuper() {
    // super.foo();
    // }

    public void invokeVirtual() {
      foo(); // invoke-virtual
    }

    public void invokeOnMember(A a) {
      // This one is illegal
      a.foo();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      B b = new B();
      b.invokeVirtual();
      b.invokeOnMember(new B());
    }
  }
}
