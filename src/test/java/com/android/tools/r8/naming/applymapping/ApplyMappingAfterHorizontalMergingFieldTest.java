// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
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
public class ApplyMappingAfterHorizontalMergingFieldTest extends TestBase {

  private static final String EXPECTED_SUCCESS = StringUtils.lines("true");

  // Will merge with B.
  public static class LibraryA {

    public static boolean bar;
  }

  // Will merge with A.
  public static class LibraryB {

    public static boolean foo;
  }

  // Ensure kept entry hitting the merged classes.
  public static class LibraryMain {

    public static void main(String[] args) {
      LibraryA.bar = System.nanoTime() > 0;
      LibraryB.foo = args.length < 123;
      System.out.println(LibraryA.bar && LibraryB.foo);
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
      NeverInline.class,
      LibraryA.class,
      LibraryB.class,
      LibraryMain.class
  };

  private static final Class<?>[] PROGRAM_CLASSES = {
      ProgramClass.class
  };

  private Backend backend;

  @Parameterized.Parameters(name = "{0}")
  public static Backend[] data() {
    return ToolHelper.getBackends();
  }

  public ApplyMappingAfterHorizontalMergingFieldTest(Backend backend) {
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
        .enableInliningAnnotations()
        .addProgramClasses(LIBRARY_CLASSES)
        .addKeepMainRule(LibraryMain.class)
        .compile();

    CodeInspector inspector = libraryResult.inspector();
    assertThat(inspector.clazz(LibraryMain.class), isPresent());
    // Classes A and B have been merged, check only one remains.
    assertTrue(inspector.clazz(LibraryA.class).isPresent()
        != inspector.clazz(LibraryB.class).isPresent());

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
