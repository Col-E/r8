// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.hamcrest.core.IsNot.not;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.debug.classes.SynchronizedBlock;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Test single stepping behaviour of synchronized blocks. */
@RunWith(Parameterized.class)
public class SynchronizedBlockTest extends DebugTestBase {

  public static final String CLASS = typeName(SynchronizedBlock.class);
  public static final String FILE = "SynchronizedBlock.java";

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean isR8;

  @Parameters(name = "{0}, R8: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().withApiLevel(AndroidApiLevel.B).build(),
        BooleanUtils.values());
  }

  public DebugTestConfig getConfig() throws Exception {
    return isR8
        ? testForR8(parameters.getBackend())
            .addProgramClasses(SynchronizedBlock.class)
            .setMinApi(parameters)
            .debug()
            .treeShaking(false)
            .minification(false)
            .addKeepAllClassesRule()
            .debugConfig(parameters.getRuntime())
        : testForRuntime(parameters)
            .addProgramClasses(SynchronizedBlock.class)
            .debugConfig(parameters.getRuntime());
  }

  @Test
  public void testEmptyBlock() throws Throwable {
    final String method = "emptyBlock";
    runDebugTest(
        getConfig(),
        CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 10),
        checkLocal("obj"),
        stepOver(),
        checkLine(FILE, 11),
        checkLocal("obj"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 12),
        checkLocal("obj"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 13),
        checkLocal("obj"),
        checkLocal("x"),
        checkLocal("y"),
        run());
  }

  @Test
  public void testNonThrowingBlock() throws Throwable {
    final String method = "nonThrowingBlock";
    runDebugTest(
        getConfig(),
        CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 17),
        checkLocal("obj"),
        stepOver(),
        checkLine(FILE, 18),
        checkLocal("obj"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 19),
        checkLocal("obj"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 20),
        checkLocal("obj"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 21),
        checkLocal("obj"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 22),
        checkLocal("obj"),
        checkLocal("x"),
        checkLocal("y"),
        run());
  }

  @Test
  public void testThrowingBlock() throws Throwable {
    Assume.assumeThat(
        "Connection timeout on 6.0.1 runtime. b/67671771",
        ToolHelper.getDexVm().getVersion(),
        not(DexVm.ART_6_0_1_TARGET.getVersion()));
    final String method = "throwingBlock";
    runDebugTest(
        getConfig(),
        CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 27),
        checkLocal("obj"),
        checkNoLocal("x"),
        stepOver(),
        checkLine(FILE, 28),
        checkLocal("obj"),
        checkLocal("x"),
        checkNoLocal("y"),
        stepOver(),
        checkLine(FILE, 29),
        checkLocal("obj"),
        checkLocal("x"),
        checkNoLocal("y"),
        stepOver(),
        checkLine(FILE, 30), // synchronized block end
        checkLocal("obj"),
        checkLocal("x"),
        checkNoLocal("y"),
        stepOver(),
        checkLine(FILE, 33), // catch handler
        checkLocal("obj"),
        checkNoLocal("x"),
        checkNoLocal("y"),
        stepOver(),
        run());
  }

  @Test
  public void testNestedNonThrowingBlock() throws Throwable {
    final String method = "nestedNonThrowingBlock";
    runDebugTest(
        getConfig(),
        CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 37),
        checkLocal("obj1"),
        checkLocal("obj2"),
        stepOver(),
        checkLine(FILE, 38),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 39),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 40),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 41),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 42),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 43),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 44),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 45),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        checkLocal("y"),
        run());
  }

  @Test
  public void testNestedThrowingBlock() throws Throwable {
    final String method = "nestedThrowingBlock";
    runDebugTest(
        getConfig(),
        CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 50),
        checkLocal("obj1"),
        checkLocal("obj2"),
        stepOver(),
        checkLine(FILE, 51),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 52),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 53),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 54),
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 55), // inner synchronize block end
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 56), // outer synchronize block end
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkLocal("x"),
        stepOver(),
        checkLine(FILE, 59), // catch handler
        checkLocal("obj1"),
        checkLocal("obj2"),
        checkNoLocal("x"),
        run());
  }
}
