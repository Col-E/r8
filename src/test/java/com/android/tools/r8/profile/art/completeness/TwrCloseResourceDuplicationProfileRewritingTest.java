// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.desugar.LibraryFilesHelper.getJdk11LibraryFiles;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.twr.TwrCloseResourceDuplicationTest;
import com.android.tools.r8.examples.JavaExampleClassProxy;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TwrCloseResourceDuplicationProfileRewritingTest
    extends TwrCloseResourceDuplicationTest {

  @Test
  public void testD8ProfileRewriting() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramFiles(TwrCloseResourceDuplicationTest.getProgramInputs())
        .addArtProfileForRewriting(getArtProfile())
        .addOptionsModification(options -> options.testing.enableSyntheticSharing = false)
        .applyIf(
            parameters.isCfRuntime(),
            testBuilder ->
                testBuilder
                    .addLibraryFiles(getJdk11LibraryFiles(temp))
                    .addDefaultRuntimeLibrary(parameters),
            testBuilder ->
                testBuilder.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST)))
        .noHorizontalClassMergingOfSynthetics()
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(this::inspectD8)
        .run(parameters.getRuntime(), MAIN.typeName(), getZipFile())
        .assertSuccessWithOutput(TwrCloseResourceDuplicationTest.EXPECTED);
  }

  @Test
  public void testR8ProfileRewriting() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramFiles(TwrCloseResourceDuplicationTest.getProgramInputs())
        .addKeepMainRule(MAIN.typeName())
        .addKeepClassAndMembersRules(FOO.typeName(), BAR.typeName())
        .addArtProfileForRewriting(getArtProfile())
        .addOptionsModification(InlinerOptions::disableInlining)
        .addOptionsModification(options -> options.testing.enableSyntheticSharing = false)
        .applyIf(
            parameters.isCfRuntime(),
            testBuilder ->
                testBuilder
                    .addLibraryFiles(getJdk11LibraryFiles(temp))
                    .addDefaultRuntimeLibrary(parameters)
                    .addOptionsModification(
                        options ->
                            options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces()),
            testBuilder ->
                testBuilder.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST)))
        .noHorizontalClassMergingOfSynthetics()
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(this::inspectR8)
        .run(parameters.getRuntime(), MAIN.typeName(), getZipFile())
        .assertSuccessWithOutput(TwrCloseResourceDuplicationTest.EXPECTED);
  }

  private ExternalArtProfile getArtProfile() {
    List<TypeReference> closeResourceFormalParameters =
        ImmutableList.of(
            Reference.classFromClass(Throwable.class),
            Reference.classFromClass(AutoCloseable.class));
    return ExternalArtProfile.builder()
        .addMethodRule(
            Reference.method(
                FOO.getClassReference(),
                "foo",
                ImmutableList.of(Reference.classFromClass(String.class)),
                null))
        .addMethodRule(
            Reference.method(
                FOO.getClassReference(), "$closeResource", closeResourceFormalParameters, null))
        .addMethodRule(
            Reference.method(
                BAR.getClassReference(),
                "bar",
                ImmutableList.of(Reference.classFromClass(String.class)),
                null))
        .addMethodRule(
            Reference.method(
                BAR.getClassReference(), "$closeResource", closeResourceFormalParameters, null))
        .build();
  }

  private boolean hasTwrCloseResourceSupport(boolean isDesugaring) {
    return !isDesugaring
        || parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelWithTwrCloseResourceSupport());
  }

  private void inspectD8(ArtProfileInspector profileInspector, CodeInspector inspector) {
    inspect(profileInspector, inspector, hasTwrCloseResourceSupport(true));
  }

  private void inspectR8(ArtProfileInspector profileInspector, CodeInspector inspector) {
    inspect(profileInspector, inspector, hasTwrCloseResourceSupport(parameters.isDexRuntime()));
  }

  private void inspect(
      ArtProfileInspector profileInspector,
      CodeInspector inspector,
      boolean hasTwrCloseResourceSupport) {
    inspector
        .allClasses()
        .forEach(c -> System.out.println(c.getDexProgramClass().toSourceString()));
    assertEquals(hasTwrCloseResourceSupport ? 3 : 15, inspector.allClasses().size());
    assertThat(inspector.clazz(MAIN.typeName()), isPresent());

    // Class Foo has two methods foo() and $closeResource().
    ClassSubject fooClassSubject = inspector.clazz(FOO.typeName());
    assertThat(fooClassSubject, isPresent());

    MethodSubject fooMethodSubject = fooClassSubject.uniqueMethodWithOriginalName("foo");
    assertThat(fooMethodSubject, isPresent());

    MethodSubject fooCloseResourceMethodSubject =
        fooClassSubject.uniqueMethodWithOriginalName("$closeResource");
    assertThat(fooCloseResourceMethodSubject, isPresent());

    // Class Bar has two methods bar() and $closeResource().
    ClassSubject barClassSubject = inspector.clazz(BAR.typeName());
    assertThat(barClassSubject, isPresent());

    MethodSubject barMethodSubject = barClassSubject.uniqueMethodWithOriginalName("bar");
    assertThat(barMethodSubject, isPresent());

    MethodSubject barCloseResourceMethodSubject =
        barClassSubject.uniqueMethodWithOriginalName("$closeResource");
    assertThat(barCloseResourceMethodSubject, isPresent());

    profileInspector.assertContainsMethodRules(
        fooMethodSubject,
        fooCloseResourceMethodSubject,
        barMethodSubject,
        barCloseResourceMethodSubject);

    // There is 1 backport, 2 synthetic API outlines, and 3 twr classes for both Foo and Bar.
    for (JavaExampleClassProxy clazz : ImmutableList.of(FOO, BAR)) {
      ClassSubject syntheticApiOutlineClassSubject0 =
          inspector.clazz(
              SyntheticItemsTestUtils.syntheticApiOutlineClass(clazz.getClassReference(), 0));
      assertThat(syntheticApiOutlineClassSubject0, notIf(isPresent(), hasTwrCloseResourceSupport));

      ClassSubject syntheticApiOutlineClassSubject1 =
          inspector.clazz(
              SyntheticItemsTestUtils.syntheticApiOutlineClass(clazz.getClassReference(), 1));
      assertThat(syntheticApiOutlineClassSubject1, notIf(isPresent(), hasTwrCloseResourceSupport));

      ClassSubject syntheticBackportClassSubject =
          inspector.clazz(
              SyntheticItemsTestUtils.syntheticBackportClass(clazz.getClassReference(), 2));
      assertThat(syntheticBackportClassSubject, notIf(isPresent(), hasTwrCloseResourceSupport));

      ClassSubject syntheticTwrCloseResourceClassSubject3 =
          inspector.clazz(
              SyntheticItemsTestUtils.syntheticTwrCloseResourceClass(clazz.getClassReference(), 3));
      assertThat(
          syntheticTwrCloseResourceClassSubject3, notIf(isPresent(), hasTwrCloseResourceSupport));

      ClassSubject syntheticTwrCloseResourceClassSubject4 =
          inspector.clazz(
              SyntheticItemsTestUtils.syntheticTwrCloseResourceClass(clazz.getClassReference(), 4));
      assertThat(
          syntheticTwrCloseResourceClassSubject4, notIf(isPresent(), hasTwrCloseResourceSupport));

      ClassSubject syntheticTwrCloseResourceClassSubject5 =
          inspector.clazz(
              SyntheticItemsTestUtils.syntheticTwrCloseResourceClass(clazz.getClassReference(), 5));
      assertThat(
          syntheticTwrCloseResourceClassSubject5, notIf(isPresent(), hasTwrCloseResourceSupport));

      profileInspector.applyIf(
          !hasTwrCloseResourceSupport,
          i ->
              i.assertContainsClassRules(
                      syntheticApiOutlineClassSubject0,
                      syntheticApiOutlineClassSubject1,
                      syntheticBackportClassSubject,
                      syntheticTwrCloseResourceClassSubject3,
                      syntheticTwrCloseResourceClassSubject4,
                      syntheticTwrCloseResourceClassSubject5)
                  .assertContainsMethodRules(
                      syntheticApiOutlineClassSubject0.uniqueMethod(),
                      syntheticApiOutlineClassSubject1.uniqueMethod(),
                      syntheticBackportClassSubject.uniqueMethod(),
                      syntheticTwrCloseResourceClassSubject3.uniqueMethod(),
                      syntheticTwrCloseResourceClassSubject4.uniqueMethod(),
                      syntheticTwrCloseResourceClassSubject5.uniqueMethod()));
    }

    profileInspector.assertContainsNoOtherRules();
  }
}
