// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.ir.desugar.records.RecordDesugaring.EQUALS_RECORD_METHOD_NAME;
import static com.android.tools.r8.ir.desugar.records.RecordDesugaring.GET_FIELDS_AS_OBJECTS_METHOD_NAME;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.ifThen;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.desugar.records.RecordTestUtils;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RecordProfileRewritingTest extends TestBase {

  private static final String RECORD_NAME = "SimpleRecord";
  private static final byte[][] PROGRAM_DATA = RecordTestUtils.getProgramData(RECORD_NAME);
  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "Jane Doe",
          "42",
          "Jane Doe",
          "42",
          "true",
          "true",
          "true",
          "false",
          "false",
          "false",
          "false");

  private static final ClassReference MAIN_REFERENCE =
      Reference.classFromTypeName(RecordTestUtils.getMainType(RECORD_NAME));
  private static final ClassReference PERSON_REFERENCE =
      Reference.classFromTypeName(MAIN_REFERENCE.getTypeName() + "$Person");
  private static final ClassReference RECORD_REFERENCE =
      Reference.classFromTypeName("java.lang.Record");
  private static final ClassReference OBJECT_REFERENCE = Reference.classFromClass(Object.class);
  private static final ClassReference STRING_REFERENCE = Reference.classFromClass(String.class);

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(parameters.canUseRecords());
    testForJvm(parameters)
        .addProgramClassFileData(PROGRAM_DATA)
        .run(parameters.getRuntime(), MAIN_REFERENCE.getTypeName())
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8() throws Exception {
    D8TestCompileResult compileResult =
        testForD8(parameters.getBackend())
            .addProgramClassFileData(PROGRAM_DATA)
            .addArtProfileForRewriting(getArtProfile())
            .setMinApi(parameters)
            .compile();
    compileResult
        .inspectResidualArtProfile(
            profileInspector ->
                compileResult.inspectWithOptions(
                    inspector -> inspectD8(profileInspector, inspector),
                    options -> options.testing.disableRecordApplicationReaderMap = true))
        .run(parameters.getRuntime(), MAIN_REFERENCE.getTypeName())
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    assumeTrue(parameters.canUseRecords() || parameters.isDexRuntime());
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClassFileData(PROGRAM_DATA)
            .addKeepMainRule(MAIN_REFERENCE.getTypeName())
            .addKeepRules(
                "-neverpropagatevalue class " + PERSON_REFERENCE.getTypeName() + " { <fields>; }")
            .addArtProfileForRewriting(getArtProfile())
            .addOptionsModification(InlinerOptions::disableInlining)
            .applyIf(
                parameters.isCfRuntime(),
                testBuilder ->
                    testBuilder.addLibraryFiles(RecordTestUtils.getJdk15LibraryFiles(temp)))
            .enableProguardTestOptions()
            .noHorizontalClassMergingOfSynthetics()
            .setMinApi(parameters)
            .compile();
    compileResult
        .inspectResidualArtProfile(
            profileInspector ->
                compileResult.inspectWithOptions(
                    inspector -> inspectR8(profileInspector, inspector),
                    options -> options.testing.disableRecordApplicationReaderMap = true))
        .run(parameters.getRuntime(), MAIN_REFERENCE.getTypeName())
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private ExternalArtProfile getArtProfile() {
    return ExternalArtProfile.builder()
        .addMethodRule(MethodReferenceUtils.mainMethod(MAIN_REFERENCE))
        .addClassRule(PERSON_REFERENCE)
        .addMethodRule(
            MethodReferenceUtils.instanceConstructor(
                PERSON_REFERENCE, STRING_REFERENCE, Reference.INT))
        .addMethodRule(
            Reference.method(PERSON_REFERENCE, "name", Collections.emptyList(), STRING_REFERENCE))
        .addMethodRule(
            Reference.method(PERSON_REFERENCE, "age", Collections.emptyList(), Reference.INT))
        .addMethodRule(
            Reference.method(
                PERSON_REFERENCE, "equals", ImmutableList.of(OBJECT_REFERENCE), Reference.BOOL))
        .addMethodRule(
            Reference.method(PERSON_REFERENCE, "hashCode", Collections.emptyList(), Reference.INT))
        .addMethodRule(
            Reference.method(
                PERSON_REFERENCE, "toString", Collections.emptyList(), STRING_REFERENCE))
        .build();
  }

  private void inspectD8(ArtProfileInspector profileInspector, CodeInspector inspector) {
    inspect(
        profileInspector,
        inspector,
        SyntheticItemsTestUtils.syntheticRecordTagClass(),
        false,
        parameters.canUseNestBasedAccessesWhenDesugaring(),
        parameters.canUseRecordsWhenDesugaring());
  }

  private void inspectR8(ArtProfileInspector profileInspector, CodeInspector inspector) {
    inspect(
        profileInspector,
        inspector,
        RECORD_REFERENCE,
        parameters.canHaveNonReboundConstructorInvoke(),
        parameters.canUseNestBasedAccesses(),
        parameters.canUseRecords());
  }

  private void inspect(
      ArtProfileInspector profileInspector,
      CodeInspector inspector,
      ClassReference recordClassReference,
      boolean canHaveNonReboundConstructorInvoke,
      boolean canUseNestBasedAccesses,
      boolean canUseRecords) {
    ClassSubject mainClassSubject = inspector.clazz(MAIN_REFERENCE);
    assertThat(mainClassSubject, isPresent());

    MethodSubject mainMethodSubject = mainClassSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());

    ClassSubject recordTagClassSubject = inspector.clazz(recordClassReference);
    assertThat(recordTagClassSubject, notIf(isPresent(), canUseRecords));
    if (!canUseRecords) {
      assertEquals(
          canHaveNonReboundConstructorInvoke ? 0 : 1, recordTagClassSubject.allMethods().size());
    }

    MethodSubject recordTagInstanceInitializerSubject = recordTagClassSubject.init();
    assertThat(
        recordTagInstanceInitializerSubject,
        notIf(isPresent(), canHaveNonReboundConstructorInvoke || canUseRecords));

    ClassSubject personRecordClassSubject = inspector.clazz(PERSON_REFERENCE);
    assertThat(personRecordClassSubject, isPresent());
    assertEquals(
        canUseRecords
            ? inspector.getTypeSubject(RECORD_REFERENCE.getTypeName())
            : recordTagClassSubject.asTypeSubject(),
        personRecordClassSubject.getSuperType());
    assertEquals(canUseRecords ? 6 : 10, personRecordClassSubject.allMethods().size());

    MethodSubject personInstanceInitializerSubject =
        personRecordClassSubject.uniqueInstanceInitializer();
    assertThat(personInstanceInitializerSubject, isPresent());

    // Name getters.
    MethodSubject nameMethodSubject = personRecordClassSubject.uniqueMethodWithOriginalName("name");
    assertThat(nameMethodSubject, isPresent());

    MethodSubject nameNestAccessorMethodSubject =
        personRecordClassSubject.uniqueMethodWithOriginalName(
            SyntheticItemsTestUtils.syntheticNestInstanceFieldGetter(
                    Reference.field(PERSON_REFERENCE, "name", STRING_REFERENCE))
                .getMethodName());
    assertThat(nameNestAccessorMethodSubject, notIf(isPresent(), canUseNestBasedAccesses));

    // Age getters.
    MethodSubject ageMethodSubject = personRecordClassSubject.uniqueMethodWithOriginalName("age");
    assertThat(ageMethodSubject, isPresent());

    MethodSubject ageNestAccessorMethodSubject =
        personRecordClassSubject.uniqueMethodWithOriginalName(
            SyntheticItemsTestUtils.syntheticNestInstanceFieldGetter(
                    Reference.field(PERSON_REFERENCE, "age", Reference.INT))
                .getMethodName());
    assertThat(ageNestAccessorMethodSubject, notIf(isPresent(), canUseNestBasedAccesses));

    // boolean equals(Object)
    MethodSubject getFieldsAsObjectsMethodSubject =
        personRecordClassSubject.uniqueMethodWithOriginalName(GET_FIELDS_AS_OBJECTS_METHOD_NAME);
    assertThat(getFieldsAsObjectsMethodSubject, notIf(isPresent(), canUseRecords));

    MethodSubject equalsHelperMethodSubject =
        personRecordClassSubject.uniqueMethodWithOriginalName(EQUALS_RECORD_METHOD_NAME);
    assertThat(equalsHelperMethodSubject, notIf(isPresent(), canUseRecords));

    MethodSubject equalsMethodSubject =
        personRecordClassSubject.uniqueMethodWithOriginalName("equals");
    assertThat(equalsMethodSubject, isPresent());
    assertThat(
        equalsMethodSubject, ifThen(!canUseRecords, invokesMethod(equalsHelperMethodSubject)));

    // int hashCode()
    ClassSubject hashCodeHelperClassSubject =
        inspector.clazz(SyntheticItemsTestUtils.syntheticRecordHelperClass(PERSON_REFERENCE, 1));
    assertThat(hashCodeHelperClassSubject, notIf(isPresent(), canUseRecords));

    MethodSubject hashCodeHelperMethodSubject = hashCodeHelperClassSubject.uniqueMethod();
    assertThat(hashCodeHelperMethodSubject, notIf(isPresent(), canUseRecords));

    MethodSubject hashCodeMethodSubject =
        personRecordClassSubject.uniqueMethodWithOriginalName("hashCode");
    assertThat(hashCodeMethodSubject, isPresent());
    assertThat(
        hashCodeMethodSubject,
        ifThen(!canUseRecords, invokesMethod(getFieldsAsObjectsMethodSubject)));
    assertThat(
        hashCodeMethodSubject, ifThen(!canUseRecords, invokesMethod(hashCodeHelperMethodSubject)));

    // String toString()
    ClassSubject toStringHelperClassSubject =
        inspector.clazz(SyntheticItemsTestUtils.syntheticRecordHelperClass(PERSON_REFERENCE, 0));
    assertThat(toStringHelperClassSubject, notIf(isPresent(), canUseRecords));

    MethodSubject toStringHelperMethodSubject = toStringHelperClassSubject.uniqueMethod();
    assertThat(toStringHelperMethodSubject, notIf(isPresent(), canUseRecords));

    MethodSubject toStringMethodSubject =
        personRecordClassSubject.uniqueMethodWithOriginalName("toString");
    assertThat(toStringMethodSubject, isPresent());
    assertThat(
        toStringMethodSubject,
        ifThen(!canUseRecords, invokesMethod(getFieldsAsObjectsMethodSubject)));
    assertThat(
        toStringMethodSubject, ifThen(!canUseRecords, invokesMethod(toStringHelperMethodSubject)));

    profileInspector
        .assertContainsClassRule(personRecordClassSubject)
        .assertContainsMethodRules(
            mainMethodSubject,
            personInstanceInitializerSubject,
            nameMethodSubject,
            ageMethodSubject,
            equalsMethodSubject,
            hashCodeMethodSubject,
            toStringMethodSubject)
        .applyIf(
            !canUseNestBasedAccesses,
            i ->
                i.assertContainsMethodRules(
                    nameNestAccessorMethodSubject, ageNestAccessorMethodSubject))
        .applyIf(
            !canUseRecords,
            i ->
                i.assertContainsClassRules(
                        recordTagClassSubject,
                        hashCodeHelperClassSubject,
                        toStringHelperClassSubject)
                    .assertContainsMethodRules(
                        equalsHelperMethodSubject,
                        getFieldsAsObjectsMethodSubject,
                        hashCodeHelperMethodSubject,
                        toStringHelperMethodSubject)
                    .applyIf(
                        !canHaveNonReboundConstructorInvoke,
                        j -> j.assertContainsMethodRule(recordTagInstanceInitializerSubject)))
        .assertContainsNoOtherRules();
  }
}
