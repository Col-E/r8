// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelClassMergingPackagePrivateTest extends TestBase {

  private final AndroidApiLevel mockLevel = AndroidApiLevel.T;
  private final String newPackageBinaryName = "package/a/";
  private final String newADescriptor = "L" + newPackageBinaryName + "A;";
  private final String newCallerDescriptor = "L" + newPackageBinaryName + "Caller;";

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public ApiModelClassMergingPackagePrivateTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private boolean isGreaterOrEqualToMockLevel() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(mockLevel);
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addProgramClasses(B.class)
        .addProgramClassFileData(
            transformer(A.class).setClassDescriptor(newADescriptor).transform(),
            transformer(Caller.class)
                .setClassDescriptor(newCallerDescriptor)
                .replaceClassDescriptorInMembers(descriptor(A.class), newADescriptor)
                .replaceClassDescriptorInMethodInstructions(descriptor(A.class), newADescriptor)
                .transform(),
            transformer(Main.class)
                .replaceClassDescriptorInMembers(descriptor(A.class), newADescriptor)
                .replaceClassDescriptorInMethodInstructions(descriptor(A.class), newADescriptor)
                .replaceClassDescriptorInMembers(descriptor(Caller.class), newCallerDescriptor)
                .replaceClassDescriptorInMethodInstructions(
                    descriptor(Caller.class), newCallerDescriptor)
                .transform())
        .addLibraryClasses(Api1.class, Api2.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(ApiModelingTestHelper::disableStubbingOfClasses)
        .apply(b -> setApiLevels(b, Api1.class))
        .apply(b -> setApiLevels(b, Api2.class));
  }

  private void setApiLevels(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder, Class<?> apiClass) {
    testBuilder
        .apply(setMockApiLevelForClass(apiClass, mockLevel))
        .apply(setMockApiLevelForDefaultInstanceInitializer(apiClass, mockLevel))
        .apply(
            setMockApiLevelForMethod(
                Reference.method(
                    Reference.classFromClass(apiClass), "foo", Collections.emptyList(), null),
                mockLevel));
  }

  @Test
  public void testD8Debug() throws Exception {
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .compile()
        .addBootClasspathClasses(Api1.class, Api2.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8Release() throws Exception {
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.RELEASE)
        .apply(this::setupTestBuilder)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .compile()
        .addBootClasspathClasses(Api1.class, Api2.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .addDontObfuscate()
        .addHorizontallyMergedClassesInspectorIf(
            parameters.isCfRuntime(), HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .addHorizontallyMergedClassesInspectorIf(
            !parameters.isCfRuntime(), this::inspectHorizontallyMergedClasses)
        .compile()
        .addBootClasspathClasses(Api1.class, Api2.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  private void inspectHorizontallyMergedClasses(HorizontallyMergedClassesInspector inspector) {
    if (isGreaterOrEqualToMockLevel()) {
      inspector.assertNoClassesMerged();
    } else {
      inspector.assertClassReferencesMerged(
          SyntheticItemsTestUtils.syntheticApiOutlineClass(Reference.classFromClass(Main.class), 0),
          SyntheticItemsTestUtils.syntheticApiOutlineClass(
              Reference.classFromDescriptor(newCallerDescriptor), 0));
    }
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    runResult.assertSuccessWithOutputLines("Api1::foo", "Api2::foo");
  }

  public static class Api1 {

    public void foo() {
      System.out.println("Api1::foo");
    }
  }

  public static class Api2 {

    public void foo() {
      System.out.println("Api2::foo");
    }
  }

  static class /* package.A. */ A extends Api1 {}

  public static class /* package.A. */ Caller {

    public static void createAndCallFoo() {
      new A().foo();
    }
  }

  static class B extends Api2 {}

  public static class Main {

    public static void main(String[] args) {
      Caller.createAndCallFoo();
      new B().foo();
    }
  }
}
