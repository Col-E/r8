// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.Jdk11TestUtils.getJdk11LibraryFiles;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.twr.TwrCloseResourceDuplicationTest;
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
  public void testR8ProfileRewriting() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(TwrCloseResourceDuplicationTest.getProgramInputs())
        .addKeepMainRule(MAIN.typeName())
        .addKeepClassAndMembersRules(FOO.typeName(), BAR.typeName())
        .addArtProfileForRewriting(getArtProfile())
        .addOptionsModification(InlinerOptions::disableInlining)
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
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspectResidualArtProfile(this::inspect)
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

  private void inspect(ArtProfileInspector profileInspector, CodeInspector inspector) {
    boolean hasTwrCloseResourceSupport =
        parameters.isCfRuntime()
            || parameters
                .getApiLevel()
                .isGreaterThanOrEqualTo(apiLevelWithTwrCloseResourceSupport());

    assertEquals(hasTwrCloseResourceSupport ? 3 : 7, inspector.allClasses().size());
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

    // There is a synthetic API outline, a backport and two twr classes.
    ClassSubject syntheticApiOutlineClassSubject =
        inspector.clazz(
            SyntheticItemsTestUtils.syntheticApiOutlineClass(BAR.getClassReference(), 0));
    assertThat(syntheticApiOutlineClassSubject, notIf(isPresent(), hasTwrCloseResourceSupport));

    ClassSubject syntheticBackportClassSubject =
        inspector.clazz(SyntheticItemsTestUtils.syntheticBackportClass(BAR.getClassReference(), 1));
    assertThat(syntheticBackportClassSubject, notIf(isPresent(), hasTwrCloseResourceSupport));

    ClassSubject syntheticTwrCloseResourceClassSubject =
        inspector.clazz(
            SyntheticItemsTestUtils.syntheticTwrCloseResourceClass(BAR.getClassReference(), 2));
    assertThat(
        syntheticTwrCloseResourceClassSubject, notIf(isPresent(), hasTwrCloseResourceSupport));

    ClassSubject otherSyntheticTwrCloseResourceClassSubject =
        inspector.clazz(
            SyntheticItemsTestUtils.syntheticTwrCloseResourceClass(BAR.getClassReference(), 3));
    assertThat(
        otherSyntheticTwrCloseResourceClassSubject, notIf(isPresent(), hasTwrCloseResourceSupport));

    // Verify that the residual profile contains all of the above.
    // TODO(b/265729283): Profile should include the twr syntehtic methods.
    profileInspector
        .assertContainsMethodRules(
            fooMethodSubject,
            fooCloseResourceMethodSubject,
            barMethodSubject,
            barCloseResourceMethodSubject)
        .applyIf(
            !hasTwrCloseResourceSupport,
            i ->
                i.assertContainsClassRules(
                        syntheticApiOutlineClassSubject, syntheticBackportClassSubject)
                    .assertContainsMethodRules(
                        syntheticApiOutlineClassSubject.uniqueMethod(),
                        syntheticBackportClassSubject.uniqueMethod()))
        .assertContainsNoOtherRules();
  }
}
