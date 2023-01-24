// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
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
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(getArtProfile())
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspectResidualArtProfile(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private ExternalArtProfile getArtProfile() throws Exception {
    return ExternalArtProfile.builder()
        .addMethodRule(Reference.methodFromMethod(I.class.getDeclaredMethod("m")))
        .build();
  }

  private void inspect(ArtProfileInspector profileInspector, CodeInspector inspector) {
    if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
      ClassSubject iClassSubject = inspector.clazz(I.class);
      assertThat(iClassSubject, isPresent());

      MethodSubject mMethodSubject = iClassSubject.uniqueMethodWithOriginalName("m");
      assertThat(mMethodSubject, isPresent());

      profileInspector.assertContainsMethodRule(mMethodSubject).assertContainsNoOtherRules();
    } else {
      ClassSubject companionClassSubject =
          inspector.clazz(SyntheticItemsTestUtils.syntheticCompanionClass(I.class));
      assertThat(companionClassSubject, isPresent());

      MethodSubject mMethodSubject = companionClassSubject.uniqueMethodWithOriginalName("m");
      assertThat(mMethodSubject, isPresent());

      // TODO(b/265729283): Should contain the companion method.
      profileInspector.assertEmpty();
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
