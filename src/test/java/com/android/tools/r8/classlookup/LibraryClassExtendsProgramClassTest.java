// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classlookup;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

// This test used to test R8 errors/warnings when library class extends program class. Before
// the change fixing b/120884788, that could easily happen as lookup would lookup in program
// classes before library classes, and the Android library included parts of JUnit, which could
// also easily end up as program classes when JUnit was used by a program.
//
// Now that library classes are looked up before program classes these JUnit classes will be
// found in the library and the ones in program will be ignored and not end up in the output.
//
// For a D8 compilation any class passed as input will end up in the output.
@RunWith(Parameterized.class)
public class LibraryClassExtendsProgramClassTest extends TestBase {

  private static List<byte[]> junitClasses;

  @BeforeClass
  public static void setUp() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    builder.addClass("junit.framework.TestCase");
    junitClasses = builder.buildClasses();
  }

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public LibraryClassExtendsProgramClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void checkClassesInResult(CodeInspector inspector) {
    if (parameters.isDexRuntime()) {
      noClassesInResult(inspector);
    } else {
      testCaseClassInResult(inspector);
    }
  }

  private void noClassesInResult(CodeInspector inspector) {
    assertEquals(1, inspector.allClasses().size());
    assertThat(inspector.clazz(TestClass.class), isPresent());
  }

  private void testCaseClassInResult(CodeInspector inspector) {
    assertEquals(2, inspector.allClasses().size());
    assertThat(inspector.clazz("junit.framework.TestCase"), isPresent());
    assertThat(inspector.clazz(TestClass.class), isPresent());
  }

  @Test
  public void testFullMode() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(junitClasses)
        .addKeepAllClassesRule()
        // TODO(120884788): Remove when this is the default.
        .addOptionsModification(options -> options.lookupLibraryBeforeProgram = true)
        .compile()
        .inspect(this::checkClassesInResult)
        .assertNoMessages();
  }

  @Test
  public void testCompatibilityMode() throws Exception {
    testForR8Compat(parameters.getBackend())
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(junitClasses)
        .addKeepAllClassesRule()
        // TODO(120884788): Remove when this is the default.
        .addOptionsModification(options -> options.lookupLibraryBeforeProgram = true)
        .compile()
        .inspect(this::checkClassesInResult)
        .assertNoMessages();
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime());
    testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(junitClasses)
        .compile()
        .inspect(this::testCaseClassInResult)
        .assertNoMessages();
  }

  @Test
  public void testFullModeError() {
    assumeTrue("Only run for Dex backend", parameters.isDexRuntime());
    try {
      testForR8(parameters.getBackend())
          .setMinApi(parameters.getApiLevel())
          .addProgramClasses(TestClass.class)
          .addProgramClassFileData(junitClasses)
          .addKeepAllClassesRule()
          .addOptionsModification(options -> options.lookupLibraryBeforeProgram = false)
          .compile();
      fail("Succeeded in full mode");
    } catch (CompilationFailedException e) {
      // Ignore.
    }
  }

  @Test
  public void testCompatibilityModeWarning() throws Exception {
    assumeTrue("Only run for Dex backend", parameters.isDexRuntime());
    R8TestCompileResult result =
        testForR8Compat(parameters.getBackend())
            .setMinApi(parameters.getApiLevel())
            .addProgramClasses(TestClass.class)
            .addProgramClassFileData(junitClasses)
            .addKeepAllClassesRule()
            .addOptionsModification(options -> options.lookupLibraryBeforeProgram = false)
            .compile()
            .assertOnlyWarnings();

    String[] libraryClassesExtendingTestCase = new String[]{
        "android.test.InstrumentationTestCase",
        "android.test.AndroidTestCase",
        "android.test.suitebuilder.TestSuiteBuilder$FailedToCreateTests"
    };

    for (String name : libraryClassesExtendingTestCase) {
      result
          .assertWarningMessageThatMatches(
              containsString(
                  "Library class " + name + " extends program class junit.framework.TestCase"));
    }
  }

  @Test
  public void testWithDontWarn() throws Exception {
    assumeTrue("Only run for Dex backend", parameters.isDexRuntime());
    testForR8(parameters.getBackend())
        .setMinApi(parameters.getApiLevel())
        .addProgramClassFileData(junitClasses)
        .addKeepAllClassesRule()
        .addKeepRules("-dontwarn android.test.**")
        .addOptionsModification(options -> options.lookupLibraryBeforeProgram = false)
        .compile()
        .assertNoMessages();
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      // Ensure that the problematic library types are actually live.
      Class.forName("android.test.InstrumentationTestCase").getDeclaredConstructor().newInstance();
      Class.forName("android.test.AndroidTestCase").getDeclaredConstructor().newInstance();
      Class.forName("android.test.suitebuilder.TestSuiteBuilder$FailedToCreateTests")
          .getDeclaredConstructor()
          .newInstance();
    }
  }
}
