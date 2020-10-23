// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.canonicalization;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SingletonCanonicalizationWithApiLevelCheckTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestBase.getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SingletonCanonicalizationWithApiLevelCheckTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Path program =
        testForR8(parameters.getBackend())
            .addProgramClasses(TestClass.class, Companion.class, A.class)
            .addLibraryClasses(LibraryVersion.class, LibraryInterfaceAddedInApi1.class)
            .addDefaultRuntimeLibrary(parameters)
            .addKeepMainRule(TestClass.class)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();

    // Run with library version 1 that includes LibraryInterfaceAddedInApi1.
    testForR8(parameters.getBackend())
        .addProgramClasses(LibraryVersion.class, LibraryInterfaceAddedInApi1.class)
        .addKeepAllClassesRule()
        .addKeepRules(getAssumeValuesRule(1))
        .setMinApi(parameters.getApiLevel())
        .compile()
        .addRunClasspathFiles(program)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("A");

    // Run with library version 0 that does not include LibraryInterfaceAddedInApi1.
    testForR8(parameters.getBackend())
        .addProgramClasses(LibraryVersion.class)
        .addKeepAllClassesRule()
        .addKeepRules(getAssumeValuesRule(0))
        .setMinApi(parameters.getApiLevel())
        .compile()
        .addRunClasspathFiles(program)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithEmptyOutput();
  }

  private List<String> getAssumeValuesRule(int version) {
    return ImmutableList.of(
        "-assumevalues class " + LibraryVersion.class.getTypeName() + " {",
        "  static int init() return " + version + ";",
        "}");
  }

  public static class LibraryVersion {

    public static int SDK_INT = init();

    private static int init() {
      return 0;
    }
  }

  public interface LibraryInterfaceAddedInApi1 {}

  static class TestClass {

    public static void main(String[] args) {
      if (LibraryVersion.SDK_INT >= 1) {
        PrintStream out = System.out;
        if (System.currentTimeMillis() > 0) {
          out.println(Companion.INSTANCE);
        } else {
          out.print(Companion.INSTANCE);
        }
      }
    }
  }

  static class Companion {

    static A INSTANCE = new A();
  }

  static class A implements LibraryInterfaceAddedInApi1 {

    @Override
    public String toString() {
      return "A";
    }
  }
}
