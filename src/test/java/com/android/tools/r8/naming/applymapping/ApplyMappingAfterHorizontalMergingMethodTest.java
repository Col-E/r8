// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
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
public class ApplyMappingAfterHorizontalMergingMethodTest extends TestBase {

  private static final String OUTPUT = "LibraryA::foo";
  private static final String EXPECTED_SUCCESS = StringUtils.lines(OUTPUT);

  // Will merge with B.
  public static class LibraryA {

    @NeverInline
    @NeverPropagateValue
    public static String bar() {
      return OUTPUT;
    }
  }

  // Will merge with A.
  public static class LibraryB {

    @NeverInline
    public static String foo() {
      return LibraryA.bar();
    }
  }

  // Ensure kept entry hitting the merged classes.
  public static class LibraryMain {

    public static void main(String[] args) {
      System.out.println(LibraryB.foo());
    }
  }

  // Program class simply calling library main.
  public static class ProgramClass {

    public static void main(String[] args) {
      LibraryMain.main(args);
    }
  }

  // Test runner code follows.

  private static final Class<?>[] LIBRARY_CLASSES = {
      LibraryA.class,
      LibraryB.class,
      LibraryMain.class
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
            .enableInliningAnnotations()
            .enableMemberValuePropagationAnnotations()
            .addProgramClasses(LIBRARY_CLASSES)
            .addKeepMainRule(LibraryMain.class)
            .setMinApi(parameters)
            .compile();

    CodeInspector inspector = libraryResult.inspector();
    assertThat(inspector.clazz(LibraryMain.class), isPresent());
    // Classes A and B have been merged, check only one remains.
    assertTrue(inspector.clazz(LibraryA.class).isPresent()
        != inspector.clazz(LibraryB.class).isPresent());

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
