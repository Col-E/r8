// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.records;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
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
public class RecordComponentSignatureTest extends TestBase {

  private static final String RECORD_NAME = "RecordWithSignature";
  private static final byte[][] PROGRAM_DATA = RecordTestUtils.getProgramData(RECORD_NAME);
  private static final String MAIN_TYPE = RecordTestUtils.getMainType(RECORD_NAME);
  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "Jane Doe",
          "42",
          "Jane Doe",
          "42",
          "true",
          "2",
          "name",
          "java.lang.CharSequence",
          "TN;",
          "0",
          "age",
          "java.lang.Object",
          "TA;",
          "0");
  private static final String EXPECTED_RESULT_R8 =
      StringUtils.lines(
          "Jane Doe", "42", "Jane Doe", "42", "true", "1", "a", "java.lang.Object", "TA;", "0");
  private static final String EXPECTED_RESULT_R8_NO_KEEP_SIGNATURE =
      StringUtils.lines(
          "Jane Doe", "42", "Jane Doe", "42", "true", "1", "a", "java.lang.Object", "null", "0");
  private static final String EXPECTED_RESULT_DESUGARED =
      StringUtils.lines("Jane Doe", "42", "Jane Doe", "42", "Class.isRecord not present");
  private static final String EXPECTED_RESULT_DESUGARED_ART_14 =
      StringUtils.lines("Jane Doe", "42", "Jane Doe", "42", "false");

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public Boolean keepSignatures;

  @Parameters(name = "{0}, keepSignatures: {1}")
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
    assumeTrue(keepSignatures);
    testForJvm(parameters)
        .addProgramClassFileData(PROGRAM_DATA)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testDesugaring() throws Exception {
    parameters.assumeDexRuntime();
    assumeTrue(keepSignatures);
    // Android U will support records.
    boolean compilingForNativeRecordSupport =
        parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.U);
    boolean runningWithNativeRecordSupport =
        parameters.getRuntime().isDex()
            && parameters.getRuntime().asDex().getVersion().isNewerThanOrEqual(Version.V14_0_0);
    testForDesugaring(
            parameters,
            options -> {
              if (compilingForNativeRecordSupport) {
                // TODO(b/231930852): When Art 14 support records this will be controlled by API
                // level.
                options.emitRecordAnnotationsInDex = true;
                options.emitRecordAnnotationsExInDex = true;
              }
            })
        .addProgramClassFileData(PROGRAM_DATA)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .applyIf(
            compilingForNativeRecordSupport,
            // Current Art 14 build does not support the java.lang.Record class.
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT),
            r ->
                r.assertSuccessWithOutput(
                        runningWithNativeRecordSupport
                            ? EXPECTED_RESULT_DESUGARED_ART_14
                            : EXPECTED_RESULT_DESUGARED)
                    .inspect(
                        inspector -> {
                          ClassSubject person =
                              inspector.clazz("records.RecordWithSignature$Person");
                          if (compilingForNativeRecordSupport) {
                            assertEquals(2, person.getFinalRecordComponents().size());

                            assertEquals(
                                "name", person.getFinalRecordComponents().get(0).getName());
                            assertTrue(
                                person
                                    .getFinalRecordComponents()
                                    .get(0)
                                    .getType()
                                    .is("java.lang.CharSequence"));
                            assertEquals(
                                "TN;", person.getFinalRecordComponents().get(0).getSignature());
                            assertEquals(
                                0,
                                person.getFinalRecordComponents().get(0).getAnnotations().size());

                            assertEquals("age", person.getFinalRecordComponents().get(1).getName());
                            assertTrue(
                                person
                                    .getFinalRecordComponents()
                                    .get(1)
                                    .getType()
                                    .is("java.lang.Object"));
                            assertEquals(
                                "TA;", person.getFinalRecordComponents().get(1).getSignature());
                            assertEquals(
                                0,
                                person.getFinalRecordComponents().get(1).getAnnotations().size());
                          } else {
                            assertEquals(0, person.getFinalRecordComponents().size());
                          }
                        }));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    boolean compilingForNativeRecordSupport =
        parameters.isCfRuntime()
            || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.U);
    boolean runningWithNativeRecordSupport =
        parameters.getRuntime().isDex()
            && parameters.getRuntime().asDex().getVersion().isNewerThanOrEqual(Version.V14_0_0);
    testForR8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        // TODO(b/231930852): Change to android.jar for Androud U when that contains
        // java.lang.Record.
        .addLibraryFiles(RecordTestUtils.getJdk15LibraryFiles(temp))
        .addKeepMainRule(MAIN_TYPE)
        .applyIf(keepSignatures, TestShrinkerBuilder::addKeepAttributeSignature)
        .setMinApi(parameters)
        .applyIf(
            compilingForNativeRecordSupport,
            // TODO(b/231930852): When Art 14 support records this will be controlled by API level.
            b ->
                b.addOptionsModification(options -> options.emitRecordAnnotationsInDex = true)
                    .addOptionsModification(options -> options.emitRecordAnnotationsExInDex = true))
        .compile()
        .inspect(
            inspector -> {
              ClassSubject person = inspector.clazz("records.RecordWithSignature$Person");
              FieldSubject age = person.uniqueFieldWithOriginalName("age");
              if (compilingForNativeRecordSupport) {
                assertThat(age, isPresentAndRenamed());
                assertEquals(1, person.getFinalRecordComponents().size());
                assertEquals(
                    age.getFinalName(), person.getFinalRecordComponents().get(0).getName());
                assertTrue(
                    person.getFinalRecordComponents().get(0).getType().is("java.lang.Object"));
                if (keepSignatures) {
                  assertEquals("TA;", person.getFinalRecordComponents().get(0).getSignature());
                } else {
                  assertNull(person.getFinalRecordComponents().get(0).getSignature());
                }
                assertEquals(0, person.getFinalRecordComponents().get(0).getAnnotations().size());
              } else {
                assertThat(age, isAbsent());
                assertEquals(0, person.getFinalRecordComponents().size());
              }
            })
        .run(parameters.getRuntime(), MAIN_TYPE)
        // No Art VM actually supports the java.lang.Record class.
        .applyIf(
            compilingForNativeRecordSupport,
            r ->
                r.assertSuccessWithOutput(
                    keepSignatures ? EXPECTED_RESULT_R8 : EXPECTED_RESULT_R8_NO_KEEP_SIGNATURE),
            r ->
                r.assertSuccessWithOutput(
                    runningWithNativeRecordSupport
                        ? EXPECTED_RESULT_DESUGARED_ART_14
                        : EXPECTED_RESULT_DESUGARED));
  }
}
