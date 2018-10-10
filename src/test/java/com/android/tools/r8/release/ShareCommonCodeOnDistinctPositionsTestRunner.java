// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.release;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.LineNumberTable;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import it.unimi.dsi.fastutil.ints.IntCollection;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ShareCommonCodeOnDistinctPositionsTestRunner extends TestBase {

  private static final Class CLASS = ShareCommonCodeOnDistinctPositionsTest.class;

  @Parameters
  public static Backend[] parameters() {
    return Backend.values();
  }

  private final Backend backend;

  public ShareCommonCodeOnDistinctPositionsTestRunner(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws CompilationFailedException, IOException, ExecutionException {
    AndroidAppConsumers sink = new AndroidAppConsumers();
    ToolHelper.runR8(
        R8Command.builder()
            .addLibraryFiles(runtimeJar(backend))
            .addProgramFiles(ToolHelper.getClassFileForTestClass(CLASS))
            .setProgramConsumer(sink.wrapProgramConsumer(emptyConsumer(backend)))
            .setDisableMinification(true)
            .setDisableTreeShaking(true)
            .build(),
        options -> options.lineNumberOptimization = LineNumberOptimization.OFF);
    CodeInspector inspector = new CodeInspector(sink.build());
    MethodSubject method = inspector.clazz(CLASS).mainMethod();
    // Check that the two shared lines are not in the output (they have no throwing instructions).
    LineNumberTable lineNumberTable = method.getLineNumberTable();
    IntCollection lines = lineNumberTable.getLines();
    assertFalse(lines.contains(12));
    assertFalse(lines.contains(14));
    // Check that the two lines have been shared, e.g., there may be only one multiplication left.
    assertEquals(
        "Expected only one multiplcation due to instruction sharing.",
        // TODO(b/117539423): Implement support for sharing optimizations in the CF backend.
        backend == Backend.DEX ? 1 : 2,
        Streams.stream(method.iterateInstructions())
            .filter(InstructionSubject::isMultiplication)
            .count());
  }
}
