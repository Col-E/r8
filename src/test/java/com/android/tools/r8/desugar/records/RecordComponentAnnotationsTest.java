// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.records;

import static com.android.tools.r8.utils.codeinspector.AnnotationMatchers.hasAnnotationTypes;
import static com.android.tools.r8.utils.codeinspector.AnnotationMatchers.hasElements;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RecordComponentAnnotationsTest extends TestBase {

  private static final String RECORD_NAME = "RecordWithAnnotations";
  private static final byte[][] PROGRAM_DATA = RecordTestUtils.getProgramData(RECORD_NAME);
  private static final String MAIN_TYPE = RecordTestUtils.getMainType(RECORD_NAME);
  private static final String JVM_EXPECTED_RESULT =
      StringUtils.lines(
          "Jane Doe",
          "42",
          "Jane Doe",
          "42",
          "true",
          "2",
          "name",
          "java.lang.String",
          "true",
          "2",
          "@records.RecordWithAnnotations$Annotation(\"a\")",
          "@records.RecordWithAnnotations$AnnotationRecordComponentOnly(\"c\")",
          "age",
          "int",
          "true",
          "2",
          "@records.RecordWithAnnotations$Annotation(\"x\")",
          "@records.RecordWithAnnotations$AnnotationRecordComponentOnly(\"z\")",
          "2",
          "2",
          "@records.RecordWithAnnotations$Annotation(\"x\")",
          "@records.RecordWithAnnotations$AnnotationFieldOnly(\"y\")",
          "2",
          "@records.RecordWithAnnotations$Annotation(\"a\")",
          "@records.RecordWithAnnotations$AnnotationFieldOnly(\"b\")");
  private static final String ART_EXPECTED_RESULT =
      StringUtils.lines(
          "Jane Doe",
          "42",
          "Jane Doe",
          "42",
          "true",
          "2",
          "name",
          "java.lang.String",
          "true",
          "2",
          "@records.RecordWithAnnotations$Annotation(value=a)",
          "@records.RecordWithAnnotations$AnnotationRecordComponentOnly(value=c)",
          "age",
          "int",
          "true",
          "2",
          "@records.RecordWithAnnotations$Annotation(value=x)",
          "@records.RecordWithAnnotations$AnnotationRecordComponentOnly(value=z)",
          "2",
          "2",
          "@records.RecordWithAnnotations$Annotation(value=x)",
          "@records.RecordWithAnnotations$AnnotationFieldOnly(value=y)",
          "2",
          "@records.RecordWithAnnotations$Annotation(value=a)",
          "@records.RecordWithAnnotations$AnnotationFieldOnly(value=b)");
  private static final String JVM_EXPECTED_RESULT_R8 =
      StringUtils.lines(
          "Jane Doe",
          "42",
          "Jane Doe",
          "42",
          "true",
          "2",
          "a",
          "java.lang.String",
          "true",
          "2",
          "@records.RecordWithAnnotations$Annotation(\"a\")",
          "@records.RecordWithAnnotations$AnnotationRecordComponentOnly(\"c\")",
          "b",
          "int",
          "true",
          "2",
          "@records.RecordWithAnnotations$Annotation(\"x\")",
          "@records.RecordWithAnnotations$AnnotationRecordComponentOnly(\"z\")",
          "2",
          "2",
          "@records.RecordWithAnnotations$Annotation(\"a\")",
          "@records.RecordWithAnnotations$AnnotationFieldOnly(\"b\")",
          "2",
          "@records.RecordWithAnnotations$Annotation(\"x\")",
          "@records.RecordWithAnnotations$AnnotationFieldOnly(\"y\")");
  private static final String ART_EXPECTED_RESULT_R8 =
      StringUtils.lines(
          "Jane Doe",
          "42",
          "Jane Doe",
          "42",
          "true",
          "2",
          "a",
          "java.lang.String",
          "true",
          "2",
          "@records.RecordWithAnnotations$Annotation(value=a)",
          "@records.RecordWithAnnotations$AnnotationRecordComponentOnly(value=c)",
          "b",
          "int",
          "true",
          "2",
          "@records.RecordWithAnnotations$Annotation(value=x)",
          "@records.RecordWithAnnotations$AnnotationRecordComponentOnly(value=z)",
          "2",
          "2",
          "@records.RecordWithAnnotations$Annotation(value=a)",
          "@records.RecordWithAnnotations$AnnotationFieldOnly(value=b)",
          "2",
          "@records.RecordWithAnnotations$Annotation(value=x)",
          "@records.RecordWithAnnotations$AnnotationFieldOnly(value=y)");
  private static final String JVM_EXPECTED_RESULT_R8_NO_KEEP_ANNOTATIONS =
      StringUtils.lines(
          "Jane Doe",
          "42",
          "Jane Doe",
          "42",
          "true",
          "2",
          "a",
          "java.lang.String",
          "true",
          "2",
          "@records.RecordWithAnnotations$Annotation(\"a\")",
          "@records.RecordWithAnnotations$AnnotationRecordComponentOnly(\"c\")",
          "b",
          "int",
          "true",
          "2",
          "@records.RecordWithAnnotations$Annotation(\"x\")",
          "@records.RecordWithAnnotations$AnnotationRecordComponentOnly(\"z\")",
          "2",
          "0",
          "0");
  private static final String ART_EXPECTED_RESULT_R8_NO_KEEP_ANNOTATIONS =
      StringUtils.lines(
          "Jane Doe",
          "42",
          "Jane Doe",
          "42",
          "true",
          "2",
          "a",
          "java.lang.String",
          "true",
          "2",
          "@records.RecordWithAnnotations$Annotation(value=a)",
          "@records.RecordWithAnnotations$AnnotationRecordComponentOnly(value=c)",
          "b",
          "int",
          "true",
          "2",
          "@records.RecordWithAnnotations$Annotation(value=x)",
          "@records.RecordWithAnnotations$AnnotationRecordComponentOnly(value=z)",
          "2",
          "0",
          "0");
  private static final String EXPECTED_RESULT_DESUGARED_RECORD_SUPPORT =
      StringUtils.lines("Jane Doe", "42", "Jane Doe", "42", "false");
  private static final String EXPECTED_RESULT_DESUGARED_NO_RECORD_SUPPORT =
      StringUtils.lines("Jane Doe", "42", "Jane Doe", "42", "Class.isRecord not present");

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public Boolean keepAnnotations;

  @Parameters(name = "{0}, keepAnnotations: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimesAndAllApiLevels()
            .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
            .withAllApiLevelsAlsoForCf()
            .build(),
        BooleanUtils.values());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(keepAnnotations);
    testForJvm(parameters)
        .addProgramClassFileData(PROGRAM_DATA)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(JVM_EXPECTED_RESULT);
  }

  @Test
  public void testDesugaring() throws Exception {
    parameters.assumeDexRuntime();
    assumeTrue(keepAnnotations);
    // Android U will support records.
    boolean compilingForNativeRecordSupport =
        parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.U);
    boolean runtimeWithNativeRecordSupport =
        parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V14_0_0);
    testForDesugaring(parameters)
        .addProgramClassFileData(PROGRAM_DATA)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .applyIf(
            compilingForNativeRecordSupport,
            r -> r.assertSuccessWithOutput(ART_EXPECTED_RESULT),
            r ->
                r.assertSuccessWithOutput(
                        !runtimeWithNativeRecordSupport
                            ? EXPECTED_RESULT_DESUGARED_NO_RECORD_SUPPORT
                            : EXPECTED_RESULT_DESUGARED_RECORD_SUPPORT)
                    .inspect(
                        inspector -> {
                          ClassSubject person =
                              inspector.clazz("records.RecordWithAnnotations$Person");
                          FieldSubject name = person.uniqueFieldWithOriginalName("name");
                          assertThat(name, isPresentAndNotRenamed());
                          FieldSubject age = person.uniqueFieldWithOriginalName("age");
                          assertThat(age, isPresentAndNotRenamed());
                          if (compilingForNativeRecordSupport) {
                            assertEquals(2, person.getFinalRecordComponents().size());

                            assertEquals(
                                name.getFinalName(),
                                person.getFinalRecordComponents().get(0).getName());
                            assertTrue(
                                person
                                    .getFinalRecordComponents()
                                    .get(0)
                                    .getType()
                                    .is("java.lang.String"));
                            assertNull(person.getFinalRecordComponents().get(0).getSignature());
                            assertEquals(
                                2,
                                person.getFinalRecordComponents().get(0).getAnnotations().size());
                            assertThat(
                                person.getFinalRecordComponents().get(0).getAnnotations(),
                                hasAnnotationTypes(
                                    inspector.getTypeSubject(
                                        "records.RecordWithAnnotations$Annotation"),
                                    inspector.getTypeSubject(
                                        "records.RecordWithAnnotations$AnnotationRecordComponentOnly")));
                            assertThat(
                                person.getFinalRecordComponents().get(0).getAnnotations().get(0),
                                hasElements(new Pair<>("value", "a")));
                            assertThat(
                                person.getFinalRecordComponents().get(0).getAnnotations().get(1),
                                hasElements(new Pair<>("value", "c")));

                            assertEquals(
                                age.getFinalName(),
                                person.getFinalRecordComponents().get(1).getName());
                            assertTrue(
                                person.getFinalRecordComponents().get(1).getType().is("int"));
                            assertNull(person.getFinalRecordComponents().get(1).getSignature());
                            assertEquals(
                                2,
                                person.getFinalRecordComponents().get(1).getAnnotations().size());
                            assertThat(
                                person.getFinalRecordComponents().get(1).getAnnotations(),
                                hasAnnotationTypes(
                                    inspector.getTypeSubject(
                                        "records.RecordWithAnnotations$Annotation"),
                                    inspector.getTypeSubject(
                                        "records.RecordWithAnnotations$AnnotationRecordComponentOnly")));
                            assertThat(
                                person.getFinalRecordComponents().get(1).getAnnotations().get(0),
                                hasElements(new Pair<>("value", "x")));
                            assertThat(
                                person.getFinalRecordComponents().get(1).getAnnotations().get(1),
                                hasElements(new Pair<>("value", "z")));
                          } else {
                            assertEquals(0, person.getFinalRecordComponents().size());
                          }
                        }));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    // Android U will support records.
    boolean compilingForNativeRecordSupport =
        parameters.isCfRuntime()
            || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.U);
    boolean runtimeWithNativeRecordSupport =
        parameters.isCfRuntime()
            || parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V14_0_0);
    testForR8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        // TODO(b/231930852): Change to android.jar for Androud U when that contains
        // java.lang.Record.
        .addLibraryFiles(RecordTestUtils.getJdk15LibraryFiles(temp))
        .addKeepMainRule(MAIN_TYPE)
        .addKeepClassAndMembersRulesWithAllowObfuscation("records.RecordWithAnnotations$Person")
        .addKeepClassAndMembersRules(
            "records.RecordWithAnnotations$Annotation",
            "records.RecordWithAnnotations$AnnotationFieldOnly",
            "records.RecordWithAnnotations$AnnotationRecordComponentOnly")
        .applyIf(keepAnnotations, TestShrinkerBuilder::addKeepRuntimeVisibleAnnotations)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject person = inspector.clazz("records.RecordWithAnnotations$Person");
              FieldSubject name = person.uniqueFieldWithOriginalName("name");
              FieldSubject age = person.uniqueFieldWithOriginalName("age");
              if (compilingForNativeRecordSupport) {
                assertEquals(2, person.getFinalRecordComponents().size());

                assertEquals(
                    name.getFinalName(), person.getFinalRecordComponents().get(0).getName());
                assertTrue(
                    person.getFinalRecordComponents().get(0).getType().is("java.lang.String"));
                assertNull(person.getFinalRecordComponents().get(0).getSignature());
                assertEquals(2, person.getFinalRecordComponents().get(0).getAnnotations().size());
                assertThat(
                    person.getFinalRecordComponents().get(0).getAnnotations(),
                    hasAnnotationTypes(
                        inspector.getTypeSubject("records.RecordWithAnnotations$Annotation"),
                        inspector.getTypeSubject(
                            "records.RecordWithAnnotations$AnnotationRecordComponentOnly")));
                assertThat(
                    person.getFinalRecordComponents().get(0).getAnnotations().get(0),
                    hasElements(new Pair<>("value", "a")));
                assertThat(
                    person.getFinalRecordComponents().get(0).getAnnotations().get(1),
                    hasElements(new Pair<>("value", "c")));

                assertEquals(
                    age.getFinalName(), person.getFinalRecordComponents().get(1).getName());
                assertTrue(person.getFinalRecordComponents().get(1).getType().is("int"));
                assertNull(person.getFinalRecordComponents().get(1).getSignature());
                assertEquals(2, person.getFinalRecordComponents().get(1).getAnnotations().size());
                assertThat(
                    person.getFinalRecordComponents().get(1).getAnnotations(),
                    hasAnnotationTypes(
                        inspector.getTypeSubject("records.RecordWithAnnotations$Annotation"),
                        inspector.getTypeSubject(
                            "records.RecordWithAnnotations$AnnotationRecordComponentOnly")));
                assertThat(
                    person.getFinalRecordComponents().get(1).getAnnotations().get(0),
                    hasElements(new Pair<>("value", "x")));
                assertThat(
                    person.getFinalRecordComponents().get(1).getAnnotations().get(1),
                    hasElements(new Pair<>("value", "z")));
              } else {
                assertEquals(0, person.getFinalRecordComponents().size());
              }
            })
        .run(parameters.getRuntime(), MAIN_TYPE)
        .applyIf(
            // TODO(b/274888318): EXPECTED_RESULT_R8_NO_KEEP_ANNOTATIONS still has component
            //  annotations.
            parameters.isCfRuntime(),
            r ->
                r.assertSuccessWithOutput(
                    keepAnnotations
                        ? JVM_EXPECTED_RESULT_R8
                        : JVM_EXPECTED_RESULT_R8_NO_KEEP_ANNOTATIONS),
            compilingForNativeRecordSupport,
            r ->
                r.assertSuccessWithOutput(
                    keepAnnotations
                        ? ART_EXPECTED_RESULT_R8
                        : ART_EXPECTED_RESULT_R8_NO_KEEP_ANNOTATIONS),
            r ->
                r.assertSuccessWithOutput(
                    runtimeWithNativeRecordSupport
                        ? EXPECTED_RESULT_DESUGARED_RECORD_SUPPORT
                        : EXPECTED_RESULT_DESUGARED_NO_RECORD_SUPPORT));
  }
}
