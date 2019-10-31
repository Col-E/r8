// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexfilemerger;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DexMergeChecksumsMarkerInterfaceTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DexMergeChecksumsMarkerInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testChecksumForMarkerInterface() throws Exception {
    Path dexArchiveI =
        testForD8()
            .setMinApi(parameters.getApiLevel())
            .addProgramClasses(I.class)
            .setMode(CompilationMode.DEBUG)
            .setIncludeClassesChecksum(true)
            .compile()
            .writeToZip();

    Path dexArchiveTestClass =
        testForD8()
            .setMinApi(parameters.getApiLevel())
            .addProgramClasses(TestClass.class)
            .setMode(CompilationMode.DEBUG)
            .setIncludeClassesChecksum(true)
            .compile()
            .writeToZip();

    testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramFiles(dexArchiveI, dexArchiveTestClass)
        .setIncludeClassesChecksum(true)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  interface I {}

  static class TestClass implements I {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
