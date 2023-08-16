// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8AndAll3Jdk11;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.DeterminismChecker;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugaredLibraryDeterminismTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8AndAll3Jdk11(),
        ImmutableList.of(D8_L8DEBUG));
  }

  public DesugaredLibraryDeterminismTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
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
    Assume.assumeTrue(libraryDesugaringSpecification.hasAnyDesugaring(parameters));

    Path libDexFile1 =
        testForL8(parameters.getApiLevel())
            .apply(libraryDesugaringSpecification::configureL8TestBuilder)
            .addOptionsModifier(
                o -> setDeterminismChecks(o, determinismLogDir, contextsRoundOne::add))
            .compile()
            .writeToZip();
    Path libDexFile2 =
        testForL8(parameters.getApiLevel())
            .apply(libraryDesugaringSpecification::configureL8TestBuilder)
            .addOptionsModifier(
                o ->
                    setDeterminismChecks(
                        o,
                        determinismLogDir,
                        context -> {
                          assertTrue(
                              "Did not find context: " + context,
                              contextsRoundOne.contains(context));
                          contextsRoundTwo.add(context);
                        }))
            .compile()
            .writeToZip();

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
