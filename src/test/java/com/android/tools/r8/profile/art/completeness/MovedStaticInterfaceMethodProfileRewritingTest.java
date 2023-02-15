// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.synthesis.SyntheticItemsTestUtils.syntheticCompanionClass;
import static com.android.tools.r8.synthesis.SyntheticItemsTestUtils.syntheticStaticInterfaceMethodAsCompanionMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MovedStaticInterfaceMethodProfileRewritingTest extends TestBase {

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
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspectResidualArtProfile(this::inspectD8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testD8FromProfileAfterDesugaring() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addArtProfileForRewriting(
            getArtProfileAfterDesugaring(
                parameters.canUseDefaultAndStaticInterfaceMethodsWhenDesugaring()))
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspectResidualArtProfile(this::inspectD8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(getArtProfile())
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspectResidualArtProfile(this::inspectR8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testR8FromProfileAfterDesugaring() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(
            getArtProfileAfterDesugaring(
                parameters.isCfRuntime() || parameters.canUseDefaultAndStaticInterfaceMethods()))
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspectResidualArtProfile(this::inspectR8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private ExternalArtProfile getArtProfile() throws Exception {
    return ExternalArtProfile.builder()
        .addMethodRule(Reference.methodFromMethod(I.class.getDeclaredMethod("m")))
        .build();
  }

  private ExternalArtProfile getArtProfileAfterDesugaring(
      boolean canUseDefaultAndStaticInterfaceMethods) throws Exception {
    if (canUseDefaultAndStaticInterfaceMethods) {
      return getArtProfile();
    } else {
      return ExternalArtProfile.builder()
          .addClassRule(syntheticCompanionClass(I.class))
          .addMethodRule(
              syntheticStaticInterfaceMethodAsCompanionMethod(I.class.getDeclaredMethod("m")))
          .build();
    }
  }

  private void inspectD8(ArtProfileInspector profileInspector, CodeInspector inspector)
      throws Exception {
    inspect(
        profileInspector,
        inspector,
        parameters.canUseDefaultAndStaticInterfaceMethodsWhenDesugaring());
  }

  private void inspectR8(ArtProfileInspector profileInspector, CodeInspector inspector)
      throws Exception {
    inspect(
        profileInspector,
        inspector,
        parameters.isCfRuntime() || parameters.canUseDefaultAndStaticInterfaceMethods());
  }

  private void inspect(
      ArtProfileInspector profileInspector,
      CodeInspector inspector,
      boolean canUseDefaultAndStaticInterfaceMethods)
      throws Exception {
    if (canUseDefaultAndStaticInterfaceMethods) {
      ClassSubject iClassSubject = inspector.clazz(I.class);
      assertThat(iClassSubject, isPresent());

      MethodSubject staticInterfaceMethodSubject = iClassSubject.uniqueMethodWithOriginalName("m");
      assertThat(staticInterfaceMethodSubject, isPresent());

      profileInspector
          .assertContainsMethodRule(staticInterfaceMethodSubject)
          .assertContainsNoOtherRules();
    } else {
      ClassSubject companionClassSubject = inspector.clazz(syntheticCompanionClass(I.class));
      assertThat(companionClassSubject, isPresent());

      MethodSubject staticInterfaceMethodSubject =
          companionClassSubject.uniqueMethodWithOriginalName("m");
      assertThat(staticInterfaceMethodSubject, isPresent());

      profileInspector
          .assertContainsClassRule(companionClassSubject)
          .assertContainsMethodRule(staticInterfaceMethodSubject)
          .assertContainsNoOtherRules();
    }
  }

  static class Main {

    public static void main(String[] args) {
      I.m();
    }
  }

  interface I {

    @NeverInline
    static void m() {
      System.out.println("Hello, world!");
    }
  }
}
