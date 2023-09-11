// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableMap;
import java.util.IdentityHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RecordBlogTest extends TestBase {

  private static final String RECORD_NAME = "RecordBlog";
  private static final byte[][] PROGRAM_DATA = RecordTestUtils.getProgramData(RECORD_NAME);
  private static final String MAIN_TYPE = RecordTestUtils.getMainType(RECORD_NAME);
  private static final String REFERENCE_OUTPUT_FORMAT = "Person[name=%s, age=42]";
  private static final String CLASS = "records.RecordBlog$Person";
  private static final Map<String, String> KEEP_RULE_TO_OUTPUT_FORMAT =
      ImmutableMap.<String, String>builder()
          .put("-dontobfuscate\n-dontoptimize", "RecordBlog$Person[name=%s, age=42]")
          .put("", "a[a=%s]")
          .put("-keep,allowshrinking class " + CLASS, "RecordBlog$Person[a=%s]")
          .put(
              "-keepclassmembers,allowshrinking,allowoptimization class "
                  + CLASS
                  + " { <fields>; }",
              "a[name=%s]")
          .put("-keep class " + CLASS + " { <fields>; }", "RecordBlog$Person[name=%s, age=42]")
          .put(
              "-keepclassmembers,allowobfuscation,allowoptimization class "
                  + CLASS
                  + " { <fields>; }",
              "a[a=%s, b=42]")
          .build();

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private boolean isCfRuntimeWithNativeRecordSupport() {
    return parameters.isCfRuntime()
        && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK14)
        && parameters.getApiLevel().equals(AndroidApiLevel.B);
  }

  private String computeOutput(String format) {
    return StringUtils.lines(String.format(format, "Jane"), String.format(format, "John"));
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(isCfRuntimeWithNativeRecordSupport());
    testForJvm(parameters)
        .addProgramClassFileData(PROGRAM_DATA)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(computeOutput(REFERENCE_OUTPUT_FORMAT));
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(PROGRAM_DATA)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN_TYPE)
        .applyIf(
            canUseNativeRecords(parameters) && !runtimeWithRecordsSupport(parameters.getRuntime()),
            r -> r.assertFailureWithErrorThatThrows(ClassNotFoundException.class),
            r -> r.assertSuccessWithOutput(computeOutput(REFERENCE_OUTPUT_FORMAT)));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    assumeTrue(parameters.isDexRuntime() || isCfRuntimeWithNativeRecordSupport());
    Map<String, String> results = new IdentityHashMap<>();
    KEEP_RULE_TO_OUTPUT_FORMAT.forEach(
        (kr, outputFormat) -> {
          try {
            R8FullTestBuilder builder =
                testForR8(parameters.getBackend())
                    .addProgramClassFileData(PROGRAM_DATA)
                    .setMinApi(parameters)
                    .addKeepRules(kr)
                    .addKeepMainRule(MAIN_TYPE);
            String res;
            if (parameters.isCfRuntime()) {
              res =
                  builder
                      .addLibraryFiles(RecordTestUtils.getJdk15LibraryFiles(temp))
                      .run(parameters.getRuntime(), MAIN_TYPE)
                      .getStdOut();
            } else {
              res = builder.run(parameters.getRuntime(), MAIN_TYPE).getStdOut();
            }
            results.put(kr, res);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    boolean success = true;
    for (String kr : KEEP_RULE_TO_OUTPUT_FORMAT.keySet()) {
      if (!computeOutput(KEEP_RULE_TO_OUTPUT_FORMAT.get(kr)).equals(results.get(kr))) {
        success = false;
      }
    }
    if (!success) {
      for (String kr : KEEP_RULE_TO_OUTPUT_FORMAT.keySet()) {
        System.out.println("==========");
        System.out.println("Keep rules:\n" + kr + "\n");
        System.out.println("Expected:\n" + computeOutput(KEEP_RULE_TO_OUTPUT_FORMAT.get(kr)));
        System.out.println("Got:\n" + results.get(kr));
      }
      System.out.println("==========");
    }
    assertTrue(success);
  }
}
