// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RecordKeepRulesTest extends TestBase {

  private static final String RECORD_NAME = "RecordShrinkField";
  private static final byte[][] PROGRAM_DATA = RecordTestUtils.getProgramData(RECORD_NAME);
  private static final String MAIN_TYPE = RecordTestUtils.getMainType(RECORD_NAME);

  private static final String KEEP_RULE_CLASS_NAME =
      "-keep,allowshrinking,allowoptimization class records.RecordShrinkField$Person";
  private static final String KEEP_RULE_FIELD_NAMES =
      "-keepclassmembers,allowshrinking,allowoptimization class records.RecordShrinkField$Person {"
          + " <fields>; }";
  private static final String KEEP_RULE_FIELDS_NO_NAMES =
      "-keepclassmembers,allowobfuscation class records.RecordShrinkField$Person { <fields>; }";
  private static final String KEEP_RULE_ALL =
      "-keep class records.RecordShrinkField$Person { <fields>; }";

  private static final String EXPECTED_RESULT_R8_WITH_CLASS_NAME =
      StringUtils.lines("RecordShrinkField$Person[a=Jane Doe]", "RecordShrinkField$Person[a=Bob]");
  private static final String EXPECTED_RESULT_R8_WITH_FIELD_NAMES =
      StringUtils.lines("a[name=Jane Doe]", "a[name=Bob]");
  private static final String EXPECTED_RESULT_R8_WITH_FIELD_NO_NAMES =
      StringUtils.lines("a[a=-1, b=Jane Doe, c=42]", "a[a=-1, b=Bob, c=42]");
  private static final String EXPECTED_RESULT_R8_WITH_ALL =
      StringUtils.lines(
          "RecordShrinkField$Person[unused=-1, name=Jane Doe, age=42]",
          "RecordShrinkField$Person[unused=-1, name=Bob, age=42]");

  private final TestParameters parameters;
  private final boolean proguardCompatibility;

  public RecordKeepRulesTest(TestParameters parameters, boolean proguardCompatibility) {
    this.parameters = parameters;
    this.proguardCompatibility = proguardCompatibility;
  }

  @Parameterized.Parameters(name = "{0}; proguardCompat: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(), BooleanUtils.values());
  }

  @Test
  public void testR8KeepRuleClassName() throws Exception {
    testR8FieldNames(KEEP_RULE_CLASS_NAME, EXPECTED_RESULT_R8_WITH_CLASS_NAME);
    testR8CfThenDexFieldNames(KEEP_RULE_CLASS_NAME, EXPECTED_RESULT_R8_WITH_CLASS_NAME);
  }

  @Test
  public void testR8KeepRuleFieldNames() throws Exception {
    testR8FieldNames(KEEP_RULE_FIELD_NAMES, EXPECTED_RESULT_R8_WITH_FIELD_NAMES);
    testR8CfThenDexFieldNames(KEEP_RULE_FIELD_NAMES, EXPECTED_RESULT_R8_WITH_FIELD_NAMES);
  }

  @Test
  public void testR8KeepRuleFieldsNoNames() throws Exception {
    testR8FieldNames(KEEP_RULE_FIELDS_NO_NAMES, EXPECTED_RESULT_R8_WITH_FIELD_NO_NAMES);
    testR8CfThenDexFieldNames(KEEP_RULE_FIELDS_NO_NAMES, EXPECTED_RESULT_R8_WITH_FIELD_NO_NAMES);
  }

  @Test
  public void testR8KeepRuleAll() throws Exception {
    testR8FieldNames(KEEP_RULE_ALL, EXPECTED_RESULT_R8_WITH_ALL);
    testR8CfThenDexFieldNames(KEEP_RULE_ALL, EXPECTED_RESULT_R8_WITH_ALL);
  }

  private void testR8FieldNames(String keepRules, String expectedOutput) throws Exception {
    testForR8Compat(parameters.getBackend(), proguardCompatibility)
        .addProgramClassFileData(PROGRAM_DATA)
        .setMinApi(parameters)
        .addKeepMainRule(MAIN_TYPE)
        .addKeepRules(keepRules)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(expectedOutput);
  }

  private void testR8CfThenDexFieldNames(String keepRules, String expectedOutput) throws Exception {
    Path desugared =
        testForR8Compat(Backend.CF, proguardCompatibility)
            .addProgramClassFileData(PROGRAM_DATA)
            .addKeepMainRule(MAIN_TYPE)
            .addKeepRules(keepRules)
            .addLibraryFiles(RecordTestUtils.getJdk15LibraryFiles(temp))
            .compile()
            .writeToZip();
    testForD8(parameters.getBackend())
        .addProgramFiles(desugared)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(expectedOutput);
  }
}
