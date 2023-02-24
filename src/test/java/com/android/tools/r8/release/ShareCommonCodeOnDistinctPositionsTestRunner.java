// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.release;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.LineNumberTable;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import it.unimi.dsi.fastutil.ints.IntCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ShareCommonCodeOnDistinctPositionsTestRunner extends TestBase {

  private static final Class<?> CLASS = ShareCommonCodeOnDistinctPositionsTest.class;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(CLASS)
        .addKeepAttributeLineNumberTable()
        .addDontObfuscate()
        .addDontShrink()
        .addOptionsModification(
            options -> options.lineNumberOptimization = LineNumberOptimization.OFF)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject method = inspector.clazz(CLASS).mainMethod();
              // Check that the two shared lines are not in the output (they have no throwing
              // instructions).
              LineNumberTable lineNumberTable = method.getLineNumberTable();
              IntCollection lines = lineNumberTable.getLines();
              assertFalse(lines.contains(12));
              assertFalse(lines.contains(14));
              // Check that the two lines have been shared, e.g., there may be only one
              // multiplication left.
              assertEquals(
                  "Expected only one multiplcation due to instruction sharing.",
                  1,
                  Streams.stream(method.iterateInstructions())
                      .filter(InstructionSubject::isMultiplication)
                      .count());
            });
  }
}
