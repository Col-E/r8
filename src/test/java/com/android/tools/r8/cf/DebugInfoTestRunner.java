// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.nio.file.Path;
import org.junit.Test;

public class DebugInfoTestRunner extends TestBase {
  static final Class CLASS = DebugInfoTest.class;

  @Test
  public void test() throws Exception {
    ProcessResult runInput =
        ToolHelper.runJava(ToolHelper.getClassPathForTests(), CLASS.getCanonicalName());
    assertEquals(0, runInput.exitCode);
    Path out1 = temp.getRoot().toPath().resolve("out1.zip");
    build(
        builder -> builder.addClassProgramData(ToolHelper.getClassAsBytes(CLASS), Origin.unknown()),
        new ClassFileConsumer.ArchiveConsumer(out1));
    ProcessResult run1 = ToolHelper.runJava(out1, CLASS.getCanonicalName());
    assertEquals(runInput.toString(), run1.toString());
    Path out2 = temp.getRoot().toPath().resolve("out2.zip");
    boolean invalidDebugInfo = false;
    try {
      build(builder -> builder.addProgramFiles(out1), new ClassFileConsumer.ArchiveConsumer(out2));
    } catch (CompilationFailedException e) {
      invalidDebugInfo = e.getCause().getMessage().contains("Invalid debug info");
    }
    // TODO(b/77522100): Change to assertFalse when fixed.
    assertTrue(invalidDebugInfo);
    if (!invalidDebugInfo) {
      ProcessResult run2 = ToolHelper.runJava(out2, CLASS.getCanonicalName());
      assertEquals(runInput.toString(), run2.toString());
    }
  }

  private void build(ThrowingConsumer<Builder, Exception> input, ProgramConsumer consumer)
      throws Exception {
    Builder builder =
        R8Command.builder()
            .setMode(CompilationMode.DEBUG)
            .addLibraryFiles(ToolHelper.getAndroidJar(ToolHelper.getMinApiLevelForDexVm()))
            .setProgramConsumer(consumer);
    input.accept(builder);
    ToolHelper.runR8(builder.build(), o -> o.invalidDebugInfoFatal = true);
  }
}
