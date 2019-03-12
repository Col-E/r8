// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping.desugar;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.util.Collection;
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
  public static Collection<TestParameters> params() {
    return getTestParameters().withCfRuntimes().withDexRuntimes().build();
  }

  private final TestParameters parameters;

  public DefaultInterfaceMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Throwable {
    Assume.assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramClasses(LibraryInterface.class, ProgramClass.class)
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testFullProgram() throws Throwable {
    testForR8(parameters.getBackend())
        .addProgramClasses(LibraryInterface.class, ProgramClass.class)
        .addKeepMainRule(ProgramClass.class)
        .apply(parameters::setMinApiForRuntime)
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testLibraryLinkedWithProgram() throws Throwable {
    R8TestCompileResult libraryResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(LibraryInterface.class)
            .addKeepRules("-keep class " + LibraryInterface.class.getTypeName() + " { *; }")
            .apply(parameters::setMinApiForRuntime)
            .compile();

    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .noTreeShaking()
            .addProgramClasses(ProgramClass.class)
            .addLibraryClasses(LibraryInterface.class)
            .addApplyMapping(libraryResult.getProguardMap())
            .apply(parameters::setMinApiForRuntime)
            .compile()
            .addRunClasspathFiles(libraryResult.writeToZip())
            .run(parameters.getRuntime(), ProgramClass.class);

    if (willDesugarDefaultInterfaceMethods(parameters.getRuntime())) {
      // TODO(b/127779880): The use of the default lambda will fail in case of desugaring.
      result.assertFailureWithErrorThatMatches(containsString("AbstractMethodError"));
    } else {
      result.assertSuccessWithOutput(EXPECTED);
    }
  }

  private static boolean willDesugarDefaultInterfaceMethods(TestRuntime runtime) {
    return runtime.isDex()
        && runtime.asDex().getMinApiLevel().getLevel() < AndroidApiLevel.N.getLevel();
  }
}
