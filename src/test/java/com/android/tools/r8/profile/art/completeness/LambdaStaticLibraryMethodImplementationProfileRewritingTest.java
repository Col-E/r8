// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Collections;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaStaticLibraryMethodImplementationProfileRewritingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addArtProfileForRewriting(getArtProfile())
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(this::inspectD8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0");
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(getArtProfile())
        .addOptionsModification(InlinerOptions::disableInlining)
        .addOptionsModification(
            options -> options.callSiteOptimizationOptions().setEnableMethodStaticizing(false))
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(this::inspectR8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0");
  }

  public ExternalArtProfile getArtProfile() {
    return ExternalArtProfile.builder()
        .addMethodRule(MethodReferenceUtils.mainMethod(Main.class))
        .build();
  }

  private void inspectD8(ArtProfileInspector profileInspector, CodeInspector inspector) {
    inspect(profileInspector, inspector, false, false);
  }

  private void inspectR8(ArtProfileInspector profileInspector, CodeInspector inspector) {
    inspect(
        profileInspector,
        inspector,
        parameters.canHaveNonReboundConstructorInvoke(),
        parameters.isCfRuntime());
  }

  public void inspect(
      ArtProfileInspector profileInspector,
      CodeInspector inspector,
      boolean canHaveNonReboundConstructorInvoke,
      boolean canUseLambdas) {
    ClassSubject mainClassSubject = inspector.clazz(Main.class);
    assertThat(mainClassSubject, isPresent());

    MethodSubject mainMethodSubject = mainClassSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());

    // Check the presence of the lambda class and its methods.
    ClassSubject lambdaClassSubject =
        inspector.clazz(SyntheticItemsTestUtils.syntheticLambdaClass(Main.class, 0));
    assertThat(lambdaClassSubject, notIf(isPresent(), canUseLambdas));

    MethodSubject lambdaInitializerSubject = lambdaClassSubject.uniqueInstanceInitializer();
    assertThat(
        lambdaInitializerSubject,
        notIf(isPresent(), canHaveNonReboundConstructorInvoke || canUseLambdas));

    MethodSubject lambdaMainMethodSubject =
        lambdaClassSubject.uniqueMethodThatMatches(FoundMethodSubject::isVirtual);
    assertThat(lambdaMainMethodSubject, notIf(isPresent(), canUseLambdas));

    if (canUseLambdas) {
      profileInspector.assertContainsMethodRule(mainMethodSubject);
    } else {
      profileInspector
          .assertContainsClassRules(lambdaClassSubject)
          .assertContainsMethodRules(mainMethodSubject, lambdaMainMethodSubject)
          .applyIf(
              !canHaveNonReboundConstructorInvoke,
              i -> i.assertContainsMethodRule(lambdaInitializerSubject));
    }

    profileInspector.assertContainsNoOtherRules();
  }

  static class Main {

    public static void main(String[] args) {
      SetSupplier lambda = Collections::emptySet;
      System.out.println(lambda.get().size());
    }
  }

  interface SetSupplier {

    Set<?> get();
  }
}
