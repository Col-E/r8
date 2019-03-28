// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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

  private Backend backend;

  @Parameterized.Parameters(name = "{0}")
  public static Backend[] data() {
    return ToolHelper.getBackends();
  }

  public ApplyMappingAfterVerticalMergingFieldTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void runOnJvm() throws Throwable {
    Assume.assumeTrue(backend == Backend.CF);
    testForJvm()
        .addProgramClasses(LIBRARY_CLASSES)
        .addProgramClasses(PROGRAM_CLASSES)
        .run(ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED_SUCCESS);
  }

  @Test
  public void b121042934() throws Exception {
    R8TestCompileResult libraryResult = testForR8(backend)
        .addProgramClasses(LIBRARY_CLASSES)
        .addKeepMainRule(LibrarySubclass.class)
        .addKeepClassAndDefaultConstructor(LibrarySubclass.class)
        .compile();

    CodeInspector inspector = libraryResult.inspector();
    assertThat(inspector.clazz(LibraryBase.class), not(isPresent()));
    assertThat(inspector.clazz(LibrarySubclass.class), isPresent());

    testForR8(backend)
        .noTreeShaking()
        .noMinification()
        .addProgramClasses(PROGRAM_CLASSES)
        .addApplyMapping(libraryResult.getProguardMap())
        .addLibraryClasses(LIBRARY_CLASSES)
        .addLibraryFiles(runtimeJar(backend))
        .compile()
        .addRunClasspathFiles(libraryResult.writeToZip())
        .run(ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED_SUCCESS);
  }
}
