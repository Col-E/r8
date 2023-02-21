// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debuginfo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.debuginfo.InliningWithoutPositionsTestSourceDump.Location;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InliningWithoutPositionsTestRunner extends TestBase {

  private static final String TEST_SIMPLE_NAME = "InliningWithoutPositionsTestSource";
  private static final String TEST_PACKAGE = "com.android.tools.r8.debuginfo";
  private static final String TEST_FILE = TEST_SIMPLE_NAME + ".java";
  private static final String TEST_CLASS = TEST_PACKAGE + "." + TEST_SIMPLE_NAME;

  private final TestParameters parameters;
  private final boolean mainPos;
  private final boolean foo1Pos;
  private final boolean barPos;
  private final boolean foo2Pos;
  private final Location throwLocation;

  @Parameters(name = "{0}: main/foo1/bar/foo2 positions: {1}/{2}/{3}/{4}, throwLocation: {5}")
  public static Collection<Object[]> data() {
    List<Object[]> testCases = new ArrayList<>();
    for (TestParameters parameters :
        TestParameters.builder().withAllRuntimes().withApiLevel(AndroidApiLevel.B).build()) {
      for (int i = 0; i < 16; ++i) {
        for (Location throwLocation : Location.values()) {
          if (throwLocation != Location.MAIN) {
            testCases.add(
                new Object[] {
                  parameters, (i & 1) != 0, (i & 2) != 0, (i & 4) != 0, (i & 8) != 0, throwLocation
                });
          }
        }
      }
    }
    return testCases;
  }

  public InliningWithoutPositionsTestRunner(
      TestParameters parameters,
      boolean mainPos,
      boolean foo1Pos,
      boolean barPos,
      boolean foo2Pos,
      Location throwLocation) {
    this.parameters = parameters;
    this.mainPos = mainPos;
    this.foo1Pos = foo1Pos;
    this.barPos = barPos;
    this.foo2Pos = foo2Pos;
    this.throwLocation = throwLocation;
  }

  @Test
  public void testStackTrace() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClassFileData(
            InliningWithoutPositionsTestSourceDump.dump(
                mainPos, foo1Pos, barPos, foo2Pos, throwLocation))
        .addKeepMainRule(TEST_CLASS)
        .addKeepAttributeSourceFile()
        .addKeepAttributeLineNumberTable()
        .addOptionsModification(
            options -> options.inlinerOptions().simpleInliningInstructionLimit = 40)
        .run(parameters.getRuntime(), TEST_CLASS)
        .assertFailure()
        .inspectOriginalStackTrace(
            rawStackTrace -> {
              assertThat(
                  rawStackTrace.getExceptionLine(),
                  CoreMatchers.containsString("<" + throwLocation + "-exception>"));
              assertTrue(rawStackTrace.size() > 1);
              StackTraceLine line = rawStackTrace.get(0);
              assertEquals(TEST_CLASS, line.className);
              assertEquals("main", line.methodName);
              // Expected line number could be PC based or increments.
              // The test need not check what it is, just that all methods have been fully
              // inlined.
              assertEquals(2, rawStackTrace.size());
            })
        .inspectStackTrace(
            retracedStackTrace -> {
              assertThat(
                  retracedStackTrace.getExceptionLine(),
                  CoreMatchers.containsString("<" + throwLocation + "-exception>"));
              switch (throwLocation) {
                case FOO1:
                  assertThat(
                      retracedStackTrace,
                      StackTrace.isSame(
                          StackTrace.builder()
                              .add(line("foo", Location.FOO1, foo1Pos))
                              .add(line("main", Location.MAIN, mainPos))
                              .build()));
                  break;
                case BAR:
                  assertThat(
                      retracedStackTrace,
                      StackTrace.isSame(
                          StackTrace.builder()
                              .add(line("bar", Location.BAR, barPos))
                              .add(line("foo", Location.FOO1, foo1Pos))
                              .add(line("main", Location.MAIN, mainPos))
                              .build()));
                  break;
                case FOO2:
                  assertThat(
                      retracedStackTrace,
                      StackTrace.isSame(
                          StackTrace.builder()
                              .add(
                                  line(
                                      "foo",
                                      foo2Pos ? Location.FOO2 : Location.FOO1,
                                      foo2Pos || foo1Pos))
                              .add(line("main", Location.MAIN, mainPos))
                              .build()));
                  break;
                default:
                  fail();
              }
            });
  }

  private StackTraceLine line(String methodName, Location location, boolean hasPosition) {
    return StackTraceLine.builder()
        .setClassName(TEST_CLASS)
        .setFileName(TEST_FILE)
        .setMethodName(methodName)
        .setLineNumber(hasPosition ? location.line : 0)
        .build();
  }
}
