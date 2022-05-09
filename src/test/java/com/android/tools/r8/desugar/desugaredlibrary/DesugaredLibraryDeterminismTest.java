// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DeterminismChecker;
import com.android.tools.r8.utils.InternalOptions;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
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

  private void setDeterminismChecks(
      InternalOptions options, Path logDir, Consumer<String> onContext) {
    options.testing.setDeterminismChecker(DeterminismChecker.createWithFileBacking(logDir));
    options.testing.processingContextsConsumer = onContext;
  }

  @Test
  public void testDeterminism() throws Exception {
    Set<String> contextsRoundOne = ConcurrentHashMap.newKeySet();
    Set<String> contextsRoundTwo = ConcurrentHashMap.newKeySet();
    Path determinismLogDir = temp.newFolder().toPath();
    AndroidApiLevel minApiLevel = parameters.getRuntime().asDex().getMinApiLevel();
    Assume.assumeTrue(minApiLevel.isLessThan(AndroidApiLevel.O));
    Path libDexFile1 =
        buildDesugaredLibrary(
            minApiLevel, o -> setDeterminismChecks(o, determinismLogDir, contextsRoundOne::add));
    Path libDexFile2 =
        buildDesugaredLibrary(
            minApiLevel,
            o ->
                setDeterminismChecks(
                    o,
                    determinismLogDir,
                    context -> {
                      assertTrue(
                          "Did not find context: " + context, contextsRoundOne.contains(context));
                      contextsRoundTwo.add(context);
                    }));
    assertEquals(contextsRoundOne, contextsRoundTwo);
    uploadJarsToCloudStorageIfTestFails(
        (file1, file2) -> {
          assertProgramsEqual(file1, file2);
          return filesAreEqual(file1, file2);
        },
        libDexFile1,
        libDexFile2);
  }
}
