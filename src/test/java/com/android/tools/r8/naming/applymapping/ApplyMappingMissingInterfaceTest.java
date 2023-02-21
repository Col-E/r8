// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApplyMappingMissingInterfaceTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    R8TestCompileResult libraryCompileResult =
        testForR8(parameters.getBackend())
            .addLibraryClasses(LibraryI.class)
            .addDefaultRuntimeLibrary(parameters)
            .addProgramClasses(ClassPathI.class)
            .setMinApi(parameters)
            .addKeepClassAndMembersRulesWithAllowObfuscation(ClassPathI.class)
            .compile();

    testForR8(parameters.getBackend())
        .addClasspathClasses(ClassPathI.class)
        .addProgramClasses(ProgramInterface.class, Main.class)
        .setMinApi(parameters)
        .addApplyMapping(libraryCompileResult.getProguardMap())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addDontWarn(LibraryI.class)
        .compile()
        .addRunClasspathFiles(libraryCompileResult.writeToZip())
        .addRunClasspathClasses(LibraryI.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Main::foo", "Main::bar");
  }

  /* Will be missing on compile time */
  public interface LibraryI {}

  public interface ClassPathI extends LibraryI {

    void foo();
  }

  public interface ProgramInterface extends ClassPathI {

    void bar();
  }

  public static class Main implements ProgramInterface {

    public static void main(String[] args) {
      new Main().foo();
      new Main().bar();
    }

    @Override
    @NeverInline
    public void foo() {
      System.out.println("Main::foo");
    }

    @Override
    @NeverInline
    public void bar() {
      System.out.println("Main::bar");
    }
  }
}
