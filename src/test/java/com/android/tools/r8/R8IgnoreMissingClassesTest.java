// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.EmbeddedOrigin;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.Test;

public class R8IgnoreMissingClassesTest {

  private static final AndroidApiLevel MIN_API = AndroidApiLevel.O;
  private static final Path EXAMPLE = Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "usestdlib.jar");
  private static final Path LIBRARY = ToolHelper.getAndroidJar(MIN_API);

  private R8Command.Builder config() {
    return R8Command.builder()
        .addProgramFiles(EXAMPLE)
        .setMinApiLevel(MIN_API.getLevel())
        .setProgramConsumer(DexIndexedConsumer.emptyConsumer());
  }

  @Test(expected = CompilationFailedException.class)
  public void testFailsWithoutLibrary() throws CompilationFailedException {
    R8.run(config().build());
  }

  @Test
  public void testPassesWithLibrary() throws CompilationFailedException {
    R8.run(config().addLibraryFiles(LIBRARY).build());
  }

  @Test
  public void testPassesWithIgnoreWarnings() throws CompilationFailedException {
    R8.run(config()
        .addProguardConfiguration(
            Collections.singletonList("-dontwarn"),
            EmbeddedOrigin.INSTANCE)
        .build());
  }
}
