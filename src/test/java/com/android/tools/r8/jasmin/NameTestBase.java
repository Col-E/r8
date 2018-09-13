// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.InvalidPathException;
import java.util.Arrays;
import java.util.Collection;

class NameTestBase extends JasminTestBase {
  // TestString is a String with modified toString() which prints \\uXXXX for
  // characters outside 0x20..0x7e.
  static class TestString {
    private final String value;

    TestString(String value) {
      this.value = value;
    }

    String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return StringUtils.toASCIIString(value);
    }
  }

  // The return value is a collection of rows with the following fields:
  // - Name (String) to test (can be class name, field name, method name).
  // - boolean, whether it runs on the JVM.
  // - boolean, whether it runs on the ART.
  static Collection<Object[]> getCommonNameTestData(boolean classNames) {

    boolean windowsSensitive = classNames && ToolHelper.isWindows();

    return Arrays.asList(
        new Object[][] {
          {new TestString("azAZ09$_"), true, true},
          {new TestString("_"), !ToolHelper.isJava9Runtime(), true},
          {new TestString("a-b"), !ToolHelper.isJava9Runtime(), true},
          {new TestString("\u00a0"), !ToolHelper.isJava9Runtime(), false},
          {new TestString("\u00a1"), !ToolHelper.isJava9Runtime(), true},
          {new TestString("\u1fff"), !ToolHelper.isJava9Runtime(), true},
          {new TestString("\u2000"), !windowsSensitive && !ToolHelper.isJava9Runtime(), false},
          {new TestString("\u200f"), !windowsSensitive && !ToolHelper.isJava9Runtime(), false},
          {new TestString("\u2010"), !windowsSensitive && !ToolHelper.isJava9Runtime(), true},
          {new TestString("\u2027"), !windowsSensitive && !ToolHelper.isJava9Runtime(), true},
          {new TestString("\u2028"), !windowsSensitive && !ToolHelper.isJava9Runtime(), false},
          {new TestString("\u202f"), !windowsSensitive && !ToolHelper.isJava9Runtime(), false},
          {new TestString("\u2030"), !windowsSensitive && !ToolHelper.isJava9Runtime(), true},
          {new TestString("\ud7ff"), !windowsSensitive && !ToolHelper.isJava9Runtime(), true},
          {new TestString("\ue000"), !windowsSensitive && !ToolHelper.isJava9Runtime(), true},
          {new TestString("\uffef"), !windowsSensitive && !ToolHelper.isJava9Runtime(), true},
          {new TestString("\ufff0"), !windowsSensitive && !ToolHelper.isJava9Runtime(), false},
          {new TestString("\uffff"), !windowsSensitive && !ToolHelper.isJava9Runtime(), false},

          // Standalone high and low surrogates.
          {new TestString("\ud800"), !classNames && !ToolHelper.isJava9Runtime(), false},
          {new TestString("\udbff"), !classNames && !ToolHelper.isJava9Runtime(), false},
          {new TestString("\udc00"), !classNames && !ToolHelper.isJava9Runtime(), false},
          {new TestString("\udfff"), !classNames && !ToolHelper.isJava9Runtime(), false},

          // Single and double code points above 0x10000.
          {new TestString("\ud800\udc00"), true, true},
          {new TestString("\ud800\udcfa"), true, true},
          {new TestString("\ud800\udcfb"), !windowsSensitive && !ToolHelper.isJava9Runtime(), true},
          {new TestString("\udbff\udfff"), !windowsSensitive && !ToolHelper.isJava9Runtime(), true},
          {new TestString("\ud800\udc00\ud800\udcfa"), true, true},
          {
            new TestString("\ud800\udc00\udbff\udfff"),
            !windowsSensitive && !ToolHelper.isJava9Runtime(),
            true
          }
        });
  }

  void runNameTesting(
      boolean validForJVM,
      JasminBuilder jasminBuilder,
      String mainClassName,
      String expectedResult,
      boolean validForArt,
      String expectedNameInFailingD8Message)
      throws Exception {

    if (validForJVM) {
      String javaResult = runOnJava(jasminBuilder, mainClassName);
      assertEquals(expectedResult, javaResult);
    } else {
      try {
        runOnJava(jasminBuilder, mainClassName);
        fail("Should have failed on JVM.");
      } catch (AssertionError | InvalidPathException e) {
        // Silent on expected failure.
      }
    }

    if (validForArt) {
      String artResult = runOnArtD8(jasminBuilder, mainClassName);
      assertEquals(expectedResult, artResult);
    } else {
      // Make sure the compiler fails.
      try {
        runOnArtD8(jasminBuilder, mainClassName);
        fail("D8 should have rejected this case.");
      } catch (CompilationFailedException t) {
        assertTrue(t.getCause().getMessage().contains(expectedNameInFailingD8Message));
      }

      // Make sure ART also fail, if D8 rejects it.
      try {
        runOnArtD8(
            jasminBuilder,
            mainClassName,
            options -> {
              options.itemFactory.setSkipNameValidationForTesting(true);
            });
        fail("Art should have failed.");
      } catch (AssertionError e) {
        // Silent on expected failure.
      }
    }
  }
}
