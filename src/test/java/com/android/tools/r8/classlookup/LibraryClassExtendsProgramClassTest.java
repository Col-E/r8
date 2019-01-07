// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classlookup;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

public class LibraryClassExtendsProgramClassTest extends TestBase {

  private static List<byte[]> junitClasses;

  @BeforeClass
  public static void setUp() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    builder.addClass("junit.framework.TestCase");
    junitClasses = builder.buildClasses();
  }

  @Test
  public void testFullModeError() {
    try {
      testForR8(Backend.DEX)
          .setMinApi(AndroidApiLevel.O)
          .addProgramClassFileData(junitClasses)
          .addKeepAllClassesRule()
          .compile();
      fail("Succeeded in full mode");
    } catch (Throwable t) {
      assertTrue(t instanceof CompilationFailedException);
    }
  }

  @Test
  public void testCompatibilityModeWarning() throws Exception {
    R8TestCompileResult result = testForR8Compat(Backend.DEX)
        .setMinApi(AndroidApiLevel.O)
        .addProgramClassFileData(junitClasses)
        .addKeepAllClassesRule()
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
    testForR8(Backend.DEX)
        .setMinApi(AndroidApiLevel.O)
        .addProgramClassFileData(junitClasses)
        .addKeepAllClassesRule()
        .addKeepRules("-dontwarn android.test.**")
        .compile()
        .assertNoMessages();
  }
}
