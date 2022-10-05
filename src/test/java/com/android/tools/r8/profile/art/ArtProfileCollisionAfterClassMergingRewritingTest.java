// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Lists;
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
    MyArtProfileProvider artProfileProvider = new MyArtProfileProvider();
    ArtProfileConsumerForTesting residualArtProfileConsumer = new ArtProfileConsumerForTesting();
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(artProfileProvider, residualArtProfileConsumer)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertMergedInto(Foo.class, Bar.class).assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .setMinApi(AndroidApiLevel.LATEST)
        .compile()
        .inspect(inspector -> inspect(inspector, residualArtProfileConsumer));
  }

  private void inspect(
      CodeInspector inspector, ArtProfileConsumerForTesting residualArtProfileConsumer) {
    ClassSubject barClassSubject = inspector.clazz(Bar.class);
    assertThat(barClassSubject, isPresentAndRenamed());

    MethodSubject helloMethodSubject = barClassSubject.uniqueMethodWithOriginalName("hello");
    assertThat(helloMethodSubject, isPresentAndRenamed());

    MethodSubject worldMethodSubject = barClassSubject.uniqueMethodWithOriginalName("world");
    assertThat(worldMethodSubject, isPresentAndRenamed());

    assertTrue(residualArtProfileConsumer.finished);
    assertEquals(
        Lists.newArrayList(
            mainClassReference,
            mainMethodReference,
            barClassSubject.getFinalReference(),
            helloMethodSubject.getFinalReference(),
            worldMethodSubject.getFinalReference()),
        residualArtProfileConsumer.references);
    assertEquals(
        Lists.newArrayList(
            ArtProfileClassRuleInfoImpl.empty(),
            ArtProfileMethodRuleInfoImpl.empty(),
            ArtProfileClassRuleInfoImpl.empty(),
            ArtProfileMethodRuleInfoImpl.empty(),
            ArtProfileMethodRuleInfoImpl.empty()),
        residualArtProfileConsumer.infos);
  }

  static class MyArtProfileProvider implements ArtProfileProvider {

    @Override
    public void getArtProfile(ArtProfileBuilder profileBuilder) {
      profileBuilder
          .addClassRule(classRuleBuilder -> classRuleBuilder.setClassReference(mainClassReference))
          .addMethodRule(
              methodRuleBuilder -> methodRuleBuilder.setMethodReference(mainMethodReference))
          .addClassRule(classRuleBuilder -> classRuleBuilder.setClassReference(fooClassReference))
          .addMethodRule(
              methodRuleBuilder -> methodRuleBuilder.setMethodReference(helloMethodReference))
          .addClassRule(classRuleBuilder -> classRuleBuilder.setClassReference(barClassReference))
          .addMethodRule(
              methodRuleBuilder -> methodRuleBuilder.setMethodReference(worldMethodReference));
    }

    @Override
    public Origin getOrigin() {
      return Origin.unknown();
    }
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
