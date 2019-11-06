// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexfilemerger;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DexMergeChecksumsFileWithNoClassesTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DexMergeChecksumsFileWithNoClassesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testChecksumWithNoClasses() throws Exception {
    Path out1 =
        testForD8()
            .setMinApi(parameters.getApiLevel())
            .setMode(CompilationMode.DEBUG)
            .setIncludeClassesChecksum(true)
            .setIntermediate(true)
            .compile()
            .inspect(this::checkContainsChecksums)
            .writeToZip();

    Path out2 =
        testForD8()
            .setMinApi(parameters.getApiLevel())
            .setMode(CompilationMode.DEBUG)
            .setIncludeClassesChecksum(true)
            .addProgramClasses(TestClass.class)
            .setIntermediate(true)
            .compile()
            .inspect(this::checkContainsChecksums)
            .writeToZip();

    testForD8()
        .addProgramFiles(out1, out2)
        .setIncludeClassesChecksum(true)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(this::checkContainsChecksums);
  }

  private void checkContainsChecksums(CodeInspector inspector) {
    inspector.getMarkers().forEach(m -> assertTrue(m.getHasChecksums()));
    // It may be prudent to check that the dex file also has the encoding string, but that is
    // not easily accessed.
    inspector.allClasses().forEach(c -> c.getDexClass().asProgramClass().getChecksum());
  }

  public static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello world!");
    }
  }
}
