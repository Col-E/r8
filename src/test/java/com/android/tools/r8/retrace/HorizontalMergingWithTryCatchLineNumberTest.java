// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.retrace.classes.SynthesizeLineNumber;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * This is a test for b/283757617 showing how we potentially could change line information at
 * runtime.
 */
@RunWith(Parameterized.class)
public class HorizontalMergingWithTryCatchLineNumberTest extends TestBase {

  private static final String FILENAME = "SynthesizeLineNumber.java";

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final StackTrace expectedStackTrace =
      StackTrace.builder()
          .add(
              StackTraceLine.builder()
                  .setClassName(typeName(SynthesizeLineNumber.A.class))
                  .setMethodName("foo")
                  .setFileName(FILENAME)
                  .setLineNumber(14)
                  .build())
          .add(
              StackTraceLine.builder()
                  .setClassName(typeName(SynthesizeLineNumber.Main.class))
                  .setMethodName("call")
                  .setFileName(FILENAME)
                  .setLineNumber(34)
                  .build())
          .add(
              StackTraceLine.builder()
                  .setClassName(typeName(SynthesizeLineNumber.Main.class))
                  .setMethodName("main")
                  .setFileName(FILENAME)
                  .setLineNumber(28)
                  .build())
          .build();

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(SynthesizeLineNumber.class)
        .run(parameters.getRuntime(), SynthesizeLineNumber.Main.class, "normal")
        .inspectStackTrace(stackTrace -> assertThat(stackTrace, isSame(expectedStackTrace)));
  }

  @Test
  public void testR8() throws Exception {
    R8TestCompileResult compilationResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(SynthesizeLineNumber.class)
            .setMinApi(parameters)
            .addKeepMainRule(SynthesizeLineNumber.Main.class)
            .addKeepAttributeLineNumberTable()
            .enableInliningAnnotations()
            .addHorizontallyMergedClassesInspector(
                inspector ->
                    inspector.assertClassesMerged(
                        SynthesizeLineNumber.A.class, SynthesizeLineNumber.B.class))
            .compile();
    // Mock changes to proguard map to simulate what R8 could emit.
    String proguardMap =
        compilationResult.getProguardMap()
            + "\n    1000:1000:void call(int,boolean):34:34 -> main"
            + "\n    1000:1000:void main(java.lang.String[]):28 -> main";
    compilationResult
        .run(parameters.getRuntime(), SynthesizeLineNumber.Main.class, "synthesize")
        .inspectOriginalStackTrace(
            originalStackTrace -> {
              StackTrace retraced = originalStackTrace.retrace(proguardMap);
              assertThat(retraced, isSame(expectedStackTrace));
            });
  }
}
