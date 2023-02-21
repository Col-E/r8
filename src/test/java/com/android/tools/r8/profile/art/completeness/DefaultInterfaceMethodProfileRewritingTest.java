// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DefaultInterfaceMethodProfileRewritingTest extends TestBase {

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
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(getArtProfile())
        .addOptionsModification(InlinerOptions::disableInlining)
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
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

  private void inspectD8(ArtProfileInspector profileInspector, CodeInspector inspector) {
    inspect(
        profileInspector,
        inspector,
        parameters.canUseDefaultAndStaticInterfaceMethodsWhenDesugaring());
  }

  private void inspectR8(ArtProfileInspector profileInspector, CodeInspector inspector) {
    inspect(
        profileInspector,
        inspector,
        parameters.isCfRuntime() || parameters.canUseDefaultAndStaticInterfaceMethods());
  }

  private void inspect(
      ArtProfileInspector profileInspector,
      CodeInspector inspector,
      boolean canUseDefaultAndStaticInterfaceMethods) {
    if (canUseDefaultAndStaticInterfaceMethods) {
      ClassSubject iClassSubject = inspector.clazz(I.class);
      assertThat(iClassSubject, isPresent());

      MethodSubject interfaceMethodSubject = iClassSubject.uniqueMethodWithOriginalName("m");
      assertThat(interfaceMethodSubject, isPresent());

      profileInspector.assertContainsMethodRule(interfaceMethodSubject);
    } else {
      ClassSubject iClassSubject = inspector.clazz(I.class);
      assertThat(iClassSubject, isPresent());

      ClassSubject aClassSubject = inspector.clazz(A.class);
      assertThat(aClassSubject, isPresent());

      ClassSubject bClassSubject = inspector.clazz(B.class);
      assertThat(bClassSubject, isPresent());

      ClassSubject companionClassSubject =
          inspector.clazz(SyntheticItemsTestUtils.syntheticCompanionClass(I.class));
      assertThat(companionClassSubject, isPresent());

      MethodSubject interfaceMethodSubject = iClassSubject.uniqueMethodWithOriginalName("m");
      assertThat(interfaceMethodSubject, isPresent());

      MethodSubject aForwardingMethodSubject =
          aClassSubject.method(interfaceMethodSubject.getFinalReference());
      assertThat(aForwardingMethodSubject, isPresent());

      MethodSubject bForwardingMethodSubject =
          bClassSubject.method(interfaceMethodSubject.getFinalReference());
      assertThat(bForwardingMethodSubject, isPresent());

      MethodSubject movedMethodSubject = companionClassSubject.uniqueMethod();
      assertThat(movedMethodSubject, isPresent());

      profileInspector
          .assertContainsClassRule(companionClassSubject)
          .assertContainsMethodRules(
              interfaceMethodSubject,
              aForwardingMethodSubject,
              bForwardingMethodSubject,
              movedMethodSubject);
    }

    profileInspector.assertContainsNoOtherRules();
  }

  static class Main {

    public static void main(String[] args) {
      I i = System.currentTimeMillis() > 0 ? new A() : new B();
      i.m();
    }
  }

  interface I {

    default void m() {
      System.out.println("Hello, world!");
    }
  }

  @NoHorizontalClassMerging
  static class A implements I {}

  @NoHorizontalClassMerging
  static class B implements I {}
}
