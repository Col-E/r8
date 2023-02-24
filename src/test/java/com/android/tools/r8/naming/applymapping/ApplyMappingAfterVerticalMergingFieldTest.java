// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApplyMappingAfterVerticalMergingFieldTest extends TestBase {

  private static final String EXPECTED_SUCCESS = StringUtils.lines("true");

  // Base class will be vertical class merged into subclass
  public static class LibraryBase {

    public boolean foo = System.nanoTime() > 0;
  }

  // Subclass targeted via vertical class merging. The main method ensures a reference to foo.
  public static class LibrarySubclass extends LibraryBase {

    public static void main(String[] args) {
      System.out.println(new LibrarySubclass().foo);
    }
  }

  // Program class that uses LibrarySubclass and its main method.
  // should thus fail at runtime.
  public static class ProgramClass extends LibrarySubclass {

    public static void main(String[] args) {
      LibrarySubclass.main(args);
    }
  }

  // Test runner code follows.

  private static final Class<?>[] LIBRARY_CLASSES = {
    LibraryBase.class, LibrarySubclass.class
  };

  private static final Class<?>[] PROGRAM_CLASSES = {
      ProgramClass.class
  };

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void runOnJvm() throws Throwable {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(LIBRARY_CLASSES)
        .addProgramClasses(PROGRAM_CLASSES)
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED_SUCCESS);
  }

  @Test
  public void b121042934() throws Exception {
    R8TestCompileResult libraryResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(LIBRARY_CLASSES)
            .addKeepMainRule(LibrarySubclass.class)
            .addKeepClassAndDefaultConstructor(LibrarySubclass.class)
            .setMinApi(parameters)
            .compile();

    CodeInspector inspector = libraryResult.inspector();
    assertThat(inspector.clazz(LibraryBase.class), not(isPresent()));
    assertThat(inspector.clazz(LibrarySubclass.class), isPresent());

    testForR8(parameters.getBackend())
        .noTreeShaking()
        .addDontObfuscate()
        .addProgramClasses(PROGRAM_CLASSES)
        .addApplyMapping(libraryResult.getProguardMap())
        .addLibraryClasses(LIBRARY_CLASSES)
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(libraryResult.writeToZip())
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED_SUCCESS);
  }
}
