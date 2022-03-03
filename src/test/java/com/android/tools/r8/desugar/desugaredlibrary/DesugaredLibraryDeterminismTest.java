// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugaredLibraryDeterminismTest extends DesugaredLibraryTestBase {
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  public DesugaredLibraryDeterminismTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDeterminism() throws Exception {
    AndroidApiLevel minApiLevel = parameters.getRuntime().asDex().getMinApiLevel();
    Assume.assumeTrue(minApiLevel.isLessThan(AndroidApiLevel.O));
    Path libDexFile1 = buildDesugaredLibrary(minApiLevel);
    Path libDexFile2 = buildDesugaredLibrary(minApiLevel);
    uploadJarsToCloudStorageIfTestFails(TestBase::filesAreEqual, libDexFile1, libDexFile2);
    assertProgramsEqual(libDexFile1, libDexFile2);
    assertTrue(filesAreEqual(libDexFile1, libDexFile2));
  }
}
