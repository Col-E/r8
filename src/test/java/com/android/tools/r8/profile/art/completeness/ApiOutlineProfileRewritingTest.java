// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiOutlineProfileRewritingTest extends TestBase {

  private static final AndroidApiLevel classApiLevel = AndroidApiLevel.M;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public AndroidApiLevel getApiLevelForRuntime() {
    return parameters.isCfRuntime()
        ? AndroidApiLevel.B
        : parameters.getRuntime().maxSupportedApiLevel();
  }

  public boolean isLibraryClassAlwaysPresent(boolean isDesugaring) {
    return !isDesugaring || parameters.getApiLevel().isGreaterThanOrEqualTo(classApiLevel);
  }

  public boolean isLibraryClassPresentInCurrentRuntime() {
    return getApiLevelForRuntime().isGreaterThanOrEqualTo(classApiLevel);
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    assumeTrue(parameters.getApiLevel() == AndroidApiLevel.B);
    assertFalse(isLibraryClassPresentInCurrentRuntime());
    testForJvm(parameters)
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::inspectRunResult);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .addArtProfileForRewriting(getArtProfile())
        .apply(setMockApiLevelForClass(LibraryClass.class, classApiLevel))
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(this::inspectD8)
        .applyIf(
            isLibraryClassPresentInCurrentRuntime(),
            testBuilder -> testBuilder.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::inspectRunResult);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(getArtProfile())
        .apply(setMockApiLevelForClass(LibraryClass.class, classApiLevel))
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(this::inspectR8)
        .applyIf(
            isLibraryClassPresentInCurrentRuntime(),
            testBuilder -> testBuilder.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::inspectRunResult);
  }

  private ExternalArtProfile getArtProfile() {
    return ExternalArtProfile.builder()
        .addMethodRule(MethodReferenceUtils.mainMethod(Main.class))
        .build();
  }

  private void inspectD8(ArtProfileInspector profileInspector, CodeInspector inspector)
      throws Exception {
    inspect(profileInspector, inspector, isLibraryClassAlwaysPresent(true));
  }

  private void inspectR8(ArtProfileInspector profileInspector, CodeInspector inspector)
      throws Exception {
    inspect(profileInspector, inspector, isLibraryClassAlwaysPresent(parameters.isDexRuntime()));
  }

  private void inspect(
      ArtProfileInspector profileInspector,
      CodeInspector inspector,
      boolean isLibraryClassAlwaysPresent)
      throws Exception {
    // Verify that outlining happened.
    verifyThat(inspector, parameters, LibraryClass.class)
        .applyIf(
            isLibraryClassAlwaysPresent,
            verifier ->
                verifier.hasNotConstClassOutlinedFrom(Main.class.getMethod("main", String[].class)),
            verifier ->
                verifier.hasConstClassOutlinedFrom(Main.class.getMethod("main", String[].class)));

    // Check outline was added to program.
    ClassSubject apiOutlineClassSubject =
        inspector.clazz(SyntheticItemsTestUtils.syntheticApiOutlineClass(Main.class, 0));
    assertThat(apiOutlineClassSubject, notIf(isPresent(), isLibraryClassAlwaysPresent));

    MethodSubject apiOutlineMethodSubject = apiOutlineClassSubject.uniqueMethod();
    assertThat(apiOutlineMethodSubject, notIf(isPresent(), isLibraryClassAlwaysPresent));

    // Verify the residual profile contains the outline method and its holder when present.
    profileInspector
        .assertContainsMethodRule(MethodReferenceUtils.mainMethod(Main.class))
        .applyIf(
            !isLibraryClassAlwaysPresent,
            i ->
                i.assertContainsClassRule(apiOutlineClassSubject)
                    .assertContainsMethodRule(apiOutlineMethodSubject))
        .assertContainsNoOtherRules();
  }

  private void inspectRunResult(SingleTestRunResult<?> runResult) {
    runResult.applyIf(
        isLibraryClassPresentInCurrentRuntime(),
        ignore -> runResult.assertSuccessWithOutputLines("class " + typeName(LibraryClass.class)),
        ignore -> runResult.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(LibraryClass.class);
    }
  }

  // Only present from api 23.
  public static class LibraryClass {}
}
