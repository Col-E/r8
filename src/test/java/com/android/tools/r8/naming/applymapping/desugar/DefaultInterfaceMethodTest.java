// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping.desugar;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DefaultInterfaceMethodTest extends TestBase {

  public static final String OUTPUT = "Called LibraryInterface::foo";
  public static final String EXPECTED = StringUtils.lines(OUTPUT);

  public interface LibraryInterface {
    default void foo() {
      System.out.println(OUTPUT);
    }
  }

  public static class ProgramClass implements LibraryInterface {

    public static void main(String[] args) {
      new ProgramClass().foo();
    }
  }

  @Parameters(name = "{0}")
  public static Backend[] data() {
    return Backend.values();
  }

  private final Backend backend;

  public DefaultInterfaceMethodTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testJvm() throws Throwable {
    Assume.assumeTrue(backend == Backend.CF);
    testForJvm()
        .addProgramClasses(LibraryInterface.class, ProgramClass.class)
        .run(ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testFullProgram() throws Throwable {
    testForR8(backend)
        .addProgramClasses(LibraryInterface.class, ProgramClass.class)
        .addKeepMainRule(ProgramClass.class)
        .run(ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testLibraryLinkedWithProgram() throws Throwable {
    R8TestCompileResult libraryResult =
        testForR8(backend)
            .addProgramClasses(LibraryInterface.class)
            .addKeepRules("-keep class " + LibraryInterface.class.getTypeName() + " { *; }")
            .compile();

    R8TestRunResult result =
        testForR8(backend)
            .noTreeShaking()
            .noMinification()
            .addProgramClasses(ProgramClass.class)
            .addLibraryClasses(LibraryInterface.class)
            .addApplyMapping(libraryResult.getProguardMap())
            .compile()
            .addRunClasspathFiles(libraryResult.writeToZip())
            .run(ProgramClass.class);

    if (backend == Backend.DEX && willDesugarDefaultInterfaceMethods()) {
      // TODO(b/127779880): The use of the default lambda will fail in case of desugaring.
      result.assertFailureWithErrorThatMatches(containsString("AbstractMethodError"));
    } else {
      result.assertSuccessWithOutput(EXPECTED);
    }
  }

  private static boolean willDesugarDefaultInterfaceMethods() {
    return ToolHelper.getMinApiLevelForDexVm().getLevel() < AndroidApiLevel.N.getLevel();
  }
}
