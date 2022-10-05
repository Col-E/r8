// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debuginfo;

import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileNameAndLineNumber;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.SourceFileEnvironment;
import com.android.tools.r8.SourceFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexDebugEntry;
import com.android.tools.r8.graph.DexDebugEntryBuilder;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnsureNoDebugInfoEmittedForPcOnlyTestRunner extends TestBase {

  private static final String FILENAME_MAIN = "EnsureNoDebugInfoEmittedForPcOnlyTest.java";
  private static final Class<?> MAIN = EnsureNoDebugInfoEmittedForPcOnlyTest.class;

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public EnsureNoDebugInfoEmittedForPcOnlyTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  private boolean apiLevelSupportsPcAndSourceFileOutput() {
    // TODO(b/146565491): Update with API level once fixed.
    return false;
  }

  @Test
  public void testD8Debug() throws Exception {
    testForD8(parameters.getBackend())
        .debug()
        .addProgramClasses(MAIN)
        .setMinApi(parameters.getApiLevel())
        .internalEnableMappingOutput()
        .run(parameters.getRuntime(), MAIN)
        // For a debug build we always expect the output to have actual line information.
        .inspectFailure(this::checkHasLineNumberInfo)
        .inspectStackTrace(this::checkExpectedStackTrace);
  }

  @Test
  public void testD8Release() throws Exception {
    testForD8(parameters.getBackend())
        .release()
        .addProgramClasses(MAIN)
        .setMinApi(parameters.getApiLevel())
        .internalEnableMappingOutput()
        .applyIf(
            apiLevelSupportsPcAndSourceFileOutput(),
            builder ->
                builder.addOptionsModification(
                    options -> {
                      options.sourceFileProvider =
                          new SourceFileProvider() {
                            @Override
                            public String get(SourceFileEnvironment environment) {
                              return null;
                            }

                            @Override
                            public boolean allowDiscardingSourceFile() {
                              return true;
                            }
                          };
                    }))
        .run(parameters.getRuntime(), MAIN)
        .inspectFailure(
            inspector -> {
              if (apiLevelSupportsPcAndSourceFileOutput()) {
                checkNoDebugInfo(inspector, 5);
              } else {
                checkHasLineNumberInfo(inspector);
              }
            })
        .inspectStackTrace(this::checkExpectedStackTrace);
  }

  @Test
  public void testD8ReleaseWithoutMapOutput() throws Exception {
    testForD8(parameters.getBackend())
        .release()
        .addProgramClasses(MAIN)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), MAIN)
        // If compiling without a map output actual debug info should also be retained. Otherwise
        // there would not be any way to obtain the actual lines.
        .inspectFailure(this::checkHasLineNumberInfo)
        .inspectStackTrace(this::checkExpectedStackTrace);
  }

  @Test
  public void testNoEmittedDebugInfoR8() throws Exception {
    assumeTrue(apiLevelSupportsPcAndSourceFileOutput());
    testForR8(parameters.getBackend())
        .addProgramClasses(MAIN)
        .addKeepMainRule(MAIN)
        .addKeepAttributeLineNumberTable()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), MAIN)
        .inspectOriginalStackTrace(
            (stackTrace, inspector) -> {
              assertEquals(MAIN.getTypeName(), stackTrace.get(0).className);
              assertEquals("main", stackTrace.get(0).methodName);
              checkNoDebugInfo(inspector, 1);
            })
        .inspectStackTrace(this::checkExpectedStackTrace);
  }

  private void checkNoDebugInfo(CodeInspector inspector, int expectedMethodsInMain) {
    ClassSubject clazz = inspector.clazz(MAIN);
    assertEquals(expectedMethodsInMain, clazz.allMethods().size());
    MethodSubject main = clazz.uniqueMethodWithOriginalName("main");
    assertNull(main.getMethod().getCode().asDexCode().getDebugInfo());
  }

  private void checkHasLineNumberInfo(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(MAIN);
    MethodSubject main = clazz.uniqueMethodWithOriginalName("main");
    List<DexDebugEntry> entries =
        new DexDebugEntryBuilder(main.getMethod(), inspector.getFactory()).build();
    Set<Integer> lines = entries.stream().map(e -> e.line).collect(Collectors.toSet());
    assertFalse(lines.isEmpty());
  }

  private void checkExpectedStackTrace(StackTrace stackTrace) {
    assertThat(
        stackTrace,
        isSameExceptForFileNameAndLineNumber(
            StackTrace.builder()
                .add(line("a", 11))
                .add(line("b", 18))
                .add(line("main", 23))
                .build()));
  }

  private StackTraceLine line(String method, int line) {
    return StackTraceLine.builder()
        .setClassName(MAIN.getTypeName())
        .setMethodName(method)
        .setLineNumber(line)
        .setFileName(FILENAME_MAIN)
        .build();
  }
}
