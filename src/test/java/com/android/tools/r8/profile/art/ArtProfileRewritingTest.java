// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.model.ExternalArtProfileClassRule;
import com.android.tools.r8.profile.art.model.ExternalArtProfileMethodRule;
import com.android.tools.r8.profile.art.utils.ArtProfileTestingUtils;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
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
public class ArtProfileRewritingTest extends TestBase {

  private static final ClassReference mainClassReference = Reference.classFromClass(Main.class);
  private static final MethodReference mainMethodReference =
      MethodReferenceUtils.mainMethod(Main.class);

  private static final ClassReference greeterClassReference =
      Reference.classFromClass(Greeter.class);
  private static final MethodReference greetMethodReference =
      MethodReferenceUtils.methodFromMethod(Greeter.class, "greet");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    Box<ExternalArtProfile> residualArtProfile = new Box<>();
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .apply(
            testBuilder ->
                ArtProfileTestingUtils.addArtProfileForRewriting(
                    getArtProfile(), residualArtProfile::set, testBuilder))
        .enableInliningAnnotations()
        .setMinApi(AndroidApiLevel.LATEST)
        .compile()
        .inspect(inspector -> inspect(inspector, residualArtProfile.get()));
  }

  private ExternalArtProfile getArtProfile() {
    return ExternalArtProfile.builder()
        .addRules(
            ExternalArtProfileClassRule.builder().setClassReference(mainClassReference).build(),
            ExternalArtProfileMethodRule.builder()
                .setMethodReference(mainMethodReference)
                .setMethodRuleInfo(ArtProfileMethodRuleInfoImpl.builder().setIsStartup().build())
                .build(),
            ExternalArtProfileClassRule.builder().setClassReference(greeterClassReference).build(),
            ExternalArtProfileMethodRule.builder()
                .setMethodReference(greetMethodReference)
                .setMethodRuleInfo(
                    ArtProfileMethodRuleInfoImpl.builder().setIsHot().setIsPostStartup().build())
                .build())
        .build();
  }

  private ExternalArtProfile getExpectedResidualArtProfile(CodeInspector inspector) {
    ClassSubject greeterClassSubject = inspector.clazz(Greeter.class);
    assertThat(greeterClassSubject, isPresentAndRenamed());

    MethodSubject greetMethodSubject = greeterClassSubject.uniqueMethodWithOriginalName("greet");
    assertThat(greetMethodSubject, isPresentAndRenamed());

    return ExternalArtProfile.builder()
        .addRules(
            ExternalArtProfileClassRule.builder().setClassReference(mainClassReference).build(),
            ExternalArtProfileMethodRule.builder()
                .setMethodReference(mainMethodReference)
                .setMethodRuleInfo(ArtProfileMethodRuleInfoImpl.builder().setIsStartup().build())
                .build(),
            ExternalArtProfileClassRule.builder()
                .setClassReference(greeterClassSubject.getFinalReference())
                .build(),
            ExternalArtProfileMethodRule.builder()
                .setMethodReference(greetMethodSubject.getFinalReference())
                .setMethodRuleInfo(
                    ArtProfileMethodRuleInfoImpl.builder().setIsHot().setIsPostStartup().build())
                .build())
        .build();
  }

  private void inspect(CodeInspector inspector, ExternalArtProfile residualArtProfile) {
    assertEquals(getExpectedResidualArtProfile(inspector), residualArtProfile);
  }

  static class Main {

    public static void main(String[] args) {
      Greeter.greet();
    }
  }

  static class Greeter {

    @NeverInline
    static void greet() {
      System.out.println("Hello, world!");
    }
  }
}
