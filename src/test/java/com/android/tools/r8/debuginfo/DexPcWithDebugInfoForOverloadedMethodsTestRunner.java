// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debuginfo;

import static com.android.tools.r8.CollectorsUtils.toSingle;
import static com.android.tools.r8.utils.codeinspector.Matchers.isInlineFrame;
import static com.android.tools.r8.utils.codeinspector.Matchers.isInlineStack;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isTopOfStackTrace;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.RetraceFrameResult;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.Matchers.LinePosition;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexPcWithDebugInfoForOverloadedMethodsTestRunner extends TestBase {

  private static final String FILENAME_INLINE = "InlineFunction.kt";
  private static final Class<?> MAIN = DexPcWithDebugInfoForOverloadedMethodsTest.class;
  private static final int MINIFIED_LINE_POSITION = 6;
  private static final String EXPECTED = "java.lang.RuntimeException: overloaded(String)42";
  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V8_1_0)
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.O)
        .build();
  }

  public DexPcWithDebugInfoForOverloadedMethodsTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testEmittedDebugInfoForOverloads()
      throws ExecutionException, CompilationFailedException, IOException, NoSuchMethodException {
    testForR8(parameters.getBackend())
        .addProgramClasses(MAIN)
        .addKeepMainRule(MAIN)
        .addKeepMethodRules(MAIN, "void overloaded(...)")
        .addKeepAttributeLineNumberTable()
        .enableAlwaysInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN)
        .assertFailureWithErrorThatMatches(containsString(EXPECTED))
        .inspectOriginalStackTrace(
            (stackTrace, inspector) -> {
              assertEquals(MAIN.getTypeName(), stackTrace.get(0).className);
              assertEquals("overloaded", stackTrace.get(0).methodName);
              assertThat(stackTrace.get(0).fileName, not("Unknown Source"));
              inspect(inspector);
            })
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              MethodSubject throwingSubject =
                  codeInspector.clazz(MAIN).method("void", "overloaded", "java.lang.String");
              assertThat(throwingSubject, isPresent());
              LinePosition inlineStack =
                  LinePosition.stack(
                      LinePosition.create(
                          Reference.methodFromMethod(
                              MAIN.getDeclaredMethod("inlinee", String.class)),
                          MINIFIED_LINE_POSITION,
                          14,
                          FILENAME_INLINE),
                      LinePosition.create(
                          Reference.methodFromMethod(
                              MAIN.getDeclaredMethod("overloaded", String.class)),
                          MINIFIED_LINE_POSITION,
                          23,
                          FILENAME_INLINE));
              RetraceFrameResult retraceResult =
                  throwingSubject
                      .streamInstructions()
                      .filter(InstructionSubject::isThrow)
                      .collect(toSingle())
                      .retraceLinePosition(codeInspector.retrace());
              assertThat(retraceResult, isInlineFrame());
              assertThat(retraceResult, isInlineStack(inlineStack));
              assertThat(
                  retraceResult,
                  isTopOfStackTrace(
                      stackTrace,
                      ImmutableList.of(MINIFIED_LINE_POSITION, MINIFIED_LINE_POSITION)));
            });
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(MAIN);
    assertThat(clazz, isPresent());
    assertEquals(3, clazz.allMethods().size());
    for (FoundMethodSubject method : clazz.allMethods()) {
      if (method.getOriginalName().equals("main")) {
        assertNull(method.getMethod().getCode().asDexCode().getDebugInfo());
      } else {
        assertEquals("overloaded", method.getOriginalName());
        assertNotNull(method.getMethod().getCode().asDexCode().getDebugInfo());
      }
    }
  }
}
