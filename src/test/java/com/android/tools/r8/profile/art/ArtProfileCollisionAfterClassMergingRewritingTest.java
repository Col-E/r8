// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.model.ExternalArtProfileClassRule;
import com.android.tools.r8.profile.art.model.ExternalArtProfileMethodRule;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
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
public class ArtProfileCollisionAfterClassMergingRewritingTest extends TestBase {

  private static final ClassReference mainClassReference = Reference.classFromClass(Main.class);
  private static final MethodReference mainMethodReference =
      MethodReferenceUtils.mainMethod(Main.class);

  private static final ClassReference fooClassReference = Reference.classFromClass(Foo.class);
  private static final MethodReference helloMethodReference =
      MethodReferenceUtils.methodFromMethod(Foo.class, "hello");

  private static final ClassReference barClassReference = Reference.classFromClass(Bar.class);
  private static final MethodReference worldMethodReference =
      MethodReferenceUtils.methodFromMethod(Bar.class, "world");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertMergedInto(Foo.class, Bar.class).assertNoOtherClassesMerged())
        .addArtProfileForRewriting(getArtProfile())
        .enableInliningAnnotations()
        .setMinApi(AndroidApiLevel.LATEST)
        .compile()
        .inspectResidualArtProfile(this::inspect);
  }

  public ExternalArtProfile getArtProfile() {
    return ExternalArtProfile.builder()
        .addRules(
            ExternalArtProfileClassRule.builder().setClassReference(mainClassReference).build(),
            ExternalArtProfileMethodRule.builder()
                .setMethodReference(mainMethodReference)
                .setMethodRuleInfo(ArtProfileMethodRuleInfoImpl.builder().setIsStartup().build())
                .build(),
            ExternalArtProfileClassRule.builder().setClassReference(fooClassReference).build(),
            ExternalArtProfileMethodRule.builder()
                .setMethodReference(helloMethodReference)
                .setMethodRuleInfo(ArtProfileMethodRuleInfoImpl.builder().setIsStartup().build())
                .build(),
            ExternalArtProfileClassRule.builder().setClassReference(barClassReference).build(),
            ExternalArtProfileMethodRule.builder()
                .setMethodReference(worldMethodReference)
                .setMethodRuleInfo(ArtProfileMethodRuleInfoImpl.builder().setIsStartup().build())
                .build())
        .build();
  }

  private void inspect(ArtProfileInspector profileInspector, CodeInspector inspector) {
    ClassSubject barClassSubject = inspector.clazz(Bar.class);
    assertThat(barClassSubject, isPresentAndRenamed());

    MethodSubject helloMethodSubject = barClassSubject.uniqueMethodWithOriginalName("hello");
    assertThat(helloMethodSubject, isPresentAndRenamed());

    MethodSubject worldMethodSubject = barClassSubject.uniqueMethodWithOriginalName("world");
    assertThat(worldMethodSubject, isPresentAndRenamed());

    profileInspector
        .assertContainsClassRules(mainClassReference, barClassSubject.getFinalReference())
        .assertContainsMethodRules(
            mainMethodReference,
            helloMethodSubject.getFinalReference(),
            worldMethodSubject.getFinalReference())
        .assertContainsNoOtherRules();
  }

  static class Main {

    public static void main(String[] args) {
      Foo.hello();
      Bar.world();
    }
  }

  static class Foo {

    @NeverInline
    static void hello() {
      System.out.print("Hello");
    }
  }

  static class Bar {

    @NeverInline
    static void world() {
      System.out.println(", world!");
    }
  }
}
