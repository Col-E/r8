// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debuginfo;

import static com.android.tools.r8.Collectors.toSingle;
import static com.android.tools.r8.utils.codeinspector.Matchers.isInlineFrame;
import static com.android.tools.r8.utils.codeinspector.Matchers.isInlineStack;
import static com.android.tools.r8.utils.codeinspector.Matchers.isTopOfStackTrace;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.RetraceMethodResult;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
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
public class EnsureNoDebugInfoEmittedForPcOnlyTestRunner extends TestBase {

  private static final String FILENAME_MAIN = "EnsureNoDebugInfoEmittedForPcOnlyTest.java";
  private static final Class<?> MAIN = EnsureNoDebugInfoEmittedForPcOnlyTest.class;
  private static final int INLINED_DEX_PC = 32;

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V8_1_0)
        .withAllApiLevels()
        .build();
  }

  public EnsureNoDebugInfoEmittedForPcOnlyTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNoEmittedDebugInfo()
      throws ExecutionException, CompilationFailedException, IOException, NoSuchMethodException {
    testForR8(parameters.getBackend())
        .addProgramClasses(MAIN)
        .addKeepMainRule(MAIN)
        .addKeepAttributeLineNumberTable()
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(
            internalOptions -> {
              // TODO(b/37830524): Remove when activated.
              internalOptions.enablePcDebugInfoOutput = true;
            })
        .run(parameters.getRuntime(), MAIN)
        .inspectOriginalStackTrace(
            (stackTrace, inspector) -> {
              assertEquals(MAIN.getTypeName(), stackTrace.get(0).className);
              assertEquals("main", stackTrace.get(0).methodName);
              inspect(inspector);
            })
        .inspectStackTrace(
            (stackTrace, codeInspector) -> {
              MethodSubject mainSubject = codeInspector.clazz(MAIN).uniqueMethodWithName("main");
              LinePosition inlineStack =
                  LinePosition.stack(
                      LinePosition.create(
                          Reference.methodFromMethod(MAIN.getDeclaredMethod("a")),
                          INLINED_DEX_PC,
                          11,
                          FILENAME_MAIN),
                      LinePosition.create(
                          Reference.methodFromMethod(MAIN.getDeclaredMethod("b")),
                          INLINED_DEX_PC,
                          18,
                          FILENAME_MAIN),
                      LinePosition.create(
                          mainSubject.asFoundMethodSubject().asMethodReference(),
                          INLINED_DEX_PC,
                          23,
                          FILENAME_MAIN));
              RetraceMethodResult retraceResult =
                  mainSubject
                      .streamInstructions()
                      .filter(InstructionSubject::isThrow)
                      .collect(toSingle())
                      .retracePcPosition(codeInspector.retrace(), mainSubject);
              assertThat(retraceResult, isInlineFrame());
              assertThat(retraceResult, isInlineStack(inlineStack));
              assertThat(
                  retraceResult,
                  isTopOfStackTrace(
                      stackTrace,
                      ImmutableList.of(INLINED_DEX_PC, INLINED_DEX_PC, INLINED_DEX_PC)));
            });
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(MAIN);
    assertEquals(1, clazz.allMethods().size());
    MethodSubject main = clazz.uniqueMethodWithName("main");
    assertNull(main.getMethod().getCode().asDexCode().getDebugInfo());
  }
}
