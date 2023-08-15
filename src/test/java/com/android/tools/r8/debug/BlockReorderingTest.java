// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.debug.classes.BlockReordering;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Test single stepping behaviour across reordered blocks. */
@RunWith(Parameterized.class)
public class BlockReorderingTest extends DebugTestBase {

  public static final String CLASS = typeName(BlockReordering.class);
  public static final String FILE = "BlockReordering.java";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParameters.builder().withDexRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  @Parameter public TestParameters parameters;

  public DebugTestConfig getDebugConfig() throws CompilationFailedException {
    return testForD8()
        .addProgramClasses(BlockReordering.class)
        .setMinApi(parameters)
        .addOptionsModification(options -> options.testing.invertConditionals = true)
        .compile()
        .debugConfig(parameters.getRuntime());
  }

  private void assumeValidTest() {
    Assume.assumeFalse(
        "Older runtimes incorrectly step out of function: b/67671565",
        parameters.isDexRuntimeVersionOlderThanOrEqual(Version.V6_0_1));
  }

  @Test
  public void testConditionalReturn() throws Throwable {
    assumeValidTest();
    final String method = "conditionalReturn";
    runDebugTest(
        getDebugConfig(),
        CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 10),
        stepOver(),
        checkLine(FILE, 15),
        run(),
        checkLine(FILE, 10),
        stepOver(),
        checkLine(FILE, 11),
        stepOver(),
        checkLine(FILE, 15),
        run());
  }

  @Test
  public void testInvertConditionalReturn() throws Throwable {
    assumeValidTest();
    final String method = "invertConditionalReturn";
    runDebugTest(
        getDebugConfig(),
        CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 19),
        stepOver(),
        checkLine(FILE, 20),
        stepOver(),
        checkLine(FILE, 24),
        run(),
        checkLine(FILE, 19),
        stepOver(),
        checkLine(FILE, 24),
        run());
  }

  @Test
  public void testFallthroughReturn() throws Throwable {
    assumeValidTest();
    final String method = "fallthroughReturn";
    runDebugTest(
        getDebugConfig(),
        CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 28),
        stepOver(),
        checkLine(FILE, 37),
        run(),
        checkLine(FILE, 28),
        stepOver(),
        checkLine(FILE, 32),
        stepOver(),
        checkLine(FILE, 37),
        run(),
        checkLine(FILE, 28),
        stepOver(),
        checkLine(FILE, 33),
        stepOver(),
        checkLine(FILE, 37),
        run());
  }
}
