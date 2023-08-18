// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bootstrap;

import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JavaBootstrapUtils extends TestBase {

  static final Path MAIN_KEEP = Paths.get("src/main/keep.txt");

  static boolean exists(Path r8WithRelocatedDeps) {
    // This test runs only if the dependencies have been generated using:
    // <code> tools/gradle.py r8WithRelocatedDeps17 </code>
    return Files.exists(r8WithRelocatedDeps);
  }

  static Path compileR8(Path r8WithRelocatedDeps, Path[] libraryFiles, boolean desugar)
      throws Exception {
    if (!exists(r8WithRelocatedDeps)) {
      return null;
    }
    // Shrink R8 11 with R8
    return testForR8(getStaticTemp(), Backend.CF)
        .addProgramFiles(r8WithRelocatedDeps)
        .addLibraryFiles(libraryFiles)
        .addKeepRuleFiles(MAIN_KEEP)
        .applyIf(
            desugar,
            builder ->
                builder.addOptionsModification(options -> options.desugarState = DesugarState.ON))
        .compile()
        .inspect(inspector -> assertNests(inspector, desugar))
        .writeToZip();
  }

  private static void assertNests(CodeInspector inspector, boolean desugar) {
    if (desugar) {
      assertTrue(
          inspector.allClasses().stream().noneMatch(subj -> subj.getDexProgramClass().isInANest()));
    } else {
      assertTrue(
          inspector.allClasses().stream().anyMatch(subj -> subj.getDexProgramClass().isInANest()));
    }
  }
}
