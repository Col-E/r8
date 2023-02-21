// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CollectionOfTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final Path INPUT_JAR =
      Paths.get(ToolHelper.EXAMPLES_JAVA9_BUILD_DIR + "collectionof.jar");
  private static final String EXPECTED_OUTPUT_BACKPORT = StringUtils.lines("false", "false");
  private static final String EXPECTED_OUTPUT_CORRECT = StringUtils.lines("npe", "npe");
  private static final String MAIN_CLASS = "collectionof.CollectionOfMain";

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public CollectionOfTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  private String getExpectedOutput(boolean desugaredLib, boolean alwaysBackportListSetMapMethods) {
    if (parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.R)) {
      return EXPECTED_OUTPUT_CORRECT;
    }
    if (alwaysBackportListSetMapMethods) {
      return EXPECTED_OUTPUT_BACKPORT;
    }
    if (desugaredLib && libraryDesugaringSpecification != JDK8) {
      if (parameters.getApiLevel().isLessThan(AndroidApiLevel.N)) {
        return EXPECTED_OUTPUT_CORRECT;
      }
      // TODO(b/243679691): This should also be correct, but is not because we use backports in
      //  partial desugaring.
      return EXPECTED_OUTPUT_BACKPORT;
    }
    return EXPECTED_OUTPUT_BACKPORT;
  }

  @Test
  public void testCollectionOf() throws Throwable {
    for (Boolean value : BooleanUtils.values()) {
      testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
          .addProgramFiles(INPUT_JAR)
          .addKeepMainRule(MAIN_CLASS)
          .addOptionsModification(opt -> opt.testing.alwaysBackportListSetMapMethods = value)
          .run(parameters.getRuntime(), MAIN_CLASS)
          .assertSuccessWithOutput(getExpectedOutput(true, value));
    }
  }

  @Test
  public void testCollectionOfReference() throws Throwable {
    Assume.assumeTrue(
        "Run only once",
        libraryDesugaringSpecification == JDK8 && compilationSpecification == D8_L8DEBUG);
    testForD8()
        .addProgramFiles(INPUT_JAR)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(getExpectedOutput(false, true));
  }
}
