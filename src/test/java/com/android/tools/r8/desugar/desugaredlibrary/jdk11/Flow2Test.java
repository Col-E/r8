// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_LEGACY;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.CustomLibrarySpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Flow2Test extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.R;
  private static final Path INPUT_JAR = Paths.get(ToolHelper.EXAMPLES_JAVA9_BUILD_DIR + "flow.jar");
  private static final Path INPUT_LIB_JAR =
      Paths.get(ToolHelper.EXAMPLES_JAVA9_BUILD_DIR + "flowlib.jar");
  private static final String EXPECTED_OUTPUT = StringUtils.lines("OneShotPublisher");
  private static final String MAIN_CLASS = "flow.FlowExample2";

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            // The actual min version is V11 but we don't have it.
            .withDexRuntimesStartingFromIncluding(Version.V12_0_0)
            .withApiLevelsEndingAtExcluding(MIN_SUPPORTED)
            .build(),
        ImmutableList.of(JDK11, JDK11_LEGACY, JDK11_PATH),
        ImmutableList.of(D8_L8DEBUG));
  }

  public Flow2Test(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void test() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramFiles(INPUT_JAR)
        .addKeepMainRule(MAIN_CLASS)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(INPUT_LIB_JAR, AndroidApiLevel.S))
        .compile()
        .inspect(this::assertWrapping)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void assertWrapping(CodeInspector inspector) {
    List<FoundMethodSubject> getPublisherMethods =
        inspector
            .clazz("flow.FlowExample2")
            .allMethods(m -> m.getMethod().getName().toString().equals("getPublisher"));
    int numMethods = parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.Q) ? 2 : 1;
    assertEquals(numMethods, getPublisherMethods.size());
  }
}
