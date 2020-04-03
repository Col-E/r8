// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.junit.Test;

public class R8CompileToInvalidFileTest extends TestBase {
  private static final Path CLASS_FILE =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR + "classes/trivial/Trivial.class");
  private static final Path INVALID_FILE = Paths.get("!@#/\\INVALID_FILE");

  @Test
  public void testCompileToInvalidFile() throws Throwable {
    ensureInvalidFileIsInvalid();
    // Verify the error is related to the missing file (and not a NPE for example).
    R8Command command =
        R8Command.builder(new DiagnosticsChecker())
            .addProgramFiles(CLASS_FILE)
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setDisableTreeShaking(true)
            .setMode(CompilationMode.DEBUG)
            .setProgramConsumer(new ClassFileConsumer.ArchiveConsumer(INVALID_FILE))
            .build();
    try {
      R8.run(command);
      fail("Excepted a CompilationFailedException but the code succeeded");
    } catch (CompilationFailedException ex) {
      assertTrue(ex.getCause().getMessage().contains("File not found"));
      assertTrue(ex.getCause().getMessage().contains(INVALID_FILE.toString()));
    } catch (Throwable t) {
      fail("Excepted a CompilationFailedException but got instead " + t);
    }
  }

  private void ensureInvalidFileIsInvalid() {
    try {
      Files.newOutputStream(
          INVALID_FILE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      fail("Excepted an IOException but the code succeeded");
    } catch (IOException ignored) {
    } catch (Throwable t) {
      fail("Excepted an IOException but got instead " + t);
    }
  }
}
