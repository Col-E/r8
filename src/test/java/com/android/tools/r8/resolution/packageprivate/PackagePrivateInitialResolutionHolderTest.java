// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.packageprivate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.LookupMethodTarget;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.OptionalBool;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PackagePrivateInitialResolutionHolderTest extends TestBase {

  private final String newCDescriptor = "La/C;";

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses()
                .addClassProgramData(getRewrittenResources())
                .addLibraryFile(parameters.getDefaultRuntimeLibrary())
                .build(),
            Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexMethod method =
        buildMethod(
            Reference.method(
                Reference.classFromDescriptor(newCDescriptor),
                "foo",
                Collections.emptyList(),
                null),
            appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
    assertTrue(resolutionResult.isSingleResolution());
    DexProgramClass programClass =
        appInfo.definitionForProgramType(
            buildType(
                Reference.classFromDescriptor(descriptor(Main.class)), appInfo.dexItemFactory()));
    assertEquals(OptionalBool.FALSE, resolutionResult.isAccessibleFrom(programClass, appView));
    DexType cType =
        buildType(Reference.classFromDescriptor(newCDescriptor), appInfo.dexItemFactory());
    DexProgramClass cClass = appView.definitionForProgramType(cType);
    LookupMethodTarget lookupMethodTarget =
        resolutionResult.lookupVirtualDispatchTarget(cClass, appInfo);
    assertEquals(
        "void " + typeName(B.class) + ".foo()",
        lookupMethodTarget.getDefinition().toSourceString());
  }

  @Test
  public void testRuntime() throws Exception {
    boolean hasIllegalAccessError =
        parameters.isCfRuntime() || parameters.getDexRuntimeVersion().isDalvik();
    testForRuntime(parameters)
        .addProgramClassFileData(getRewrittenResources())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrowsIf(hasIllegalAccessError, IllegalAccessError.class)
        // TODO(b/264523290): Should be IllegalAccessError.
        .assertSuccessWithOutputLinesIf(!hasIllegalAccessError, "B::foo");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getRewrittenResources())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/264522833): Should be IllegalAccessError, but member rebinding "fixes" the code.
        .assertFailureWithErrorThatThrows(NullPointerException.class);
  }

  private Collection<byte[]> getRewrittenResources() throws Exception {
    String newCBuilderDescriptor = "La/CBuilder;";
    return ImmutableList.of(
        transformer(B.class).transform(),
        transformer(C.class).setClassDescriptor(newCDescriptor).transform(),
        transformer(CBuilder.class)
            .replaceClassDescriptorInMethodInstructions(descriptor(C.class), newCDescriptor)
            .replaceClassDescriptorInMembers(descriptor(C.class), newCDescriptor)
            .setClassDescriptor(newCBuilderDescriptor)
            .transform(),
        transformer(Main.class)
            .replaceClassDescriptorInMembers(descriptor(C.class), newCDescriptor)
            .replaceClassDescriptorInMethodInstructions(descriptor(C.class), newCDescriptor)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(CBuilder.class), newCBuilderDescriptor)
            .transform());
  }

  public static class Main {

    public static void main(String[] args) {
      CBuilder.build().foo();
    }
  }

  public static class B {

    public void foo() {
      System.out.println("B::foo");
    }
  }

  static class /* a. */ C extends B {}

  public static class /* a. */ CBuilder {

    public static C build() {
      return new C();
    }
  }
}
