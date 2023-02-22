// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryClassInheritingFromProgramClassNamingTest extends TestBase {

  private final TestParameters parameters;
  private final Class[] LIBRARY_CLASSES =
      new Class[] {AndroidTestCase.class, ApplicationTestCase.class};

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public LibraryClassInheritingFromProgramClassNamingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws ExecutionException, CompilationFailedException, IOException {
    R8TestCompileResult libraryResult =
        testForR8(parameters.getBackend())
            .setMinApi(parameters)
            .addProgramClasses(LIBRARY_CLASSES)
            .addProgramClasses(
                I.class, Assert.class, TestCase.class, ApplicationTestCaseInProgram.class)
            .addKeepAllClassesRule()
            .compile();
    testForR8Compat(parameters.getBackend())
        .addLibraryClasses(LIBRARY_CLASSES)
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .addProgramClasses(
            I.class, Assert.class, TestCase.class, ApplicationTestCaseInProgram.class, Main.class)
        .addKeepClassAndMembersRulesWithAllowObfuscation(
            I.class, Assert.class, TestCase.class, ApplicationTestCaseInProgram.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .allowDiagnosticWarningMessages()
        .compile()
        .assertAllWarningMessagesMatch(
            containsString(
                "Library class "
                    + AndroidTestCase.class.getTypeName()
                    + " extends program class "
                    + TestCase.class.getTypeName()))
        .addRunClasspathFiles(libraryResult.writeToZip())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("TestCase.foo");
  }

  public interface I {

    void foo();
  }

  public static class Assert implements I {

    @Override
    public void foo() {
      System.out.println("Assert.foo");
    }
  }

  public static class TestCase extends Assert {

    @Override
    public void foo() {
      System.out.println("TestCase.foo");
    }
  }

  public static class AndroidTestCase extends TestCase {}

  public static class ApplicationTestCase extends AndroidTestCase {}

  public static class ApplicationTestCaseInProgram extends AndroidTestCase {}

  public static class Main {

    public static void main(String[] args) {
      new ApplicationTestCaseInProgram().foo();
    }
  }
}
