// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BackportProfileRewritingTest extends TestBase {

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
        .assertSuccessWithOutputLines("true");
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(getArtProfile())
        .addOptionsModification(InlinerOptions::disableInlining)
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(this::inspectR8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("true");
  }

  private ExternalArtProfile getArtProfile() {
    return ExternalArtProfile.builder()
        .addMethodRule(MethodReferenceUtils.mainMethod(Main.class))
        .build();
  }

  private boolean isBackportingObjectsNonNull(boolean isDesugaring) {
    return isDesugaring && parameters.getApiLevel().isLessThan(AndroidApiLevel.N);
  }

  private void inspectD8(ArtProfileInspector profileInspector, CodeInspector inspector) {
    inspect(profileInspector, inspector, isBackportingObjectsNonNull(true));
  }

  private void inspectR8(ArtProfileInspector profileInspector, CodeInspector inspector) {
    inspect(profileInspector, inspector, isBackportingObjectsNonNull(parameters.isDexRuntime()));
  }

  private void inspect(
      ArtProfileInspector profileInspector,
      CodeInspector inspector,
      boolean isBackportingObjectsNonNull) {
    ClassSubject backportClassSubject =
        inspector.clazz(SyntheticItemsTestUtils.syntheticBackportClass(Main.class, 0));
    assertThat(backportClassSubject, onlyIf(isBackportingObjectsNonNull, isPresent()));

    MethodSubject backportMethodSubject = backportClassSubject.uniqueMethod();
    assertThat(backportMethodSubject, onlyIf(isBackportingObjectsNonNull, isPresent()));

    // Verify residual profile contains the backported method and its holder.
    profileInspector
        .assertContainsMethodRules(MethodReferenceUtils.mainMethod(Main.class))
        .applyIf(
            isBackportingObjectsNonNull,
            i ->
                i.assertContainsClassRule(backportClassSubject)
                    .assertContainsMethodRule(backportMethodSubject))
        .assertContainsNoOtherRules();
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(Objects.nonNull(args));
    }
  }
}
