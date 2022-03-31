// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
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
  static Collection<Object[]> getCommonNameTestData() {
    boolean supportSpaces = ToolHelper.getMinApiLevelForDexVm().getLevel()
        >= AndroidApiLevel.R.getLevel();
    return Arrays.asList(
        new Object[][] {
          {new TestString("azAZ09$_"), true, true},
          {new TestString("_"), false, true},
          {new TestString("a-b"), false, true},
          {new TestString("\u00a0"), false, supportSpaces},
          {new TestString("\u00a1"), false, true},
          {new TestString("\u1fff"), false, true},
          {new TestString("\u2000"), false, supportSpaces},
          {new TestString("\u200f"), false, false},
          {new TestString("\u2010"), false, true},
          {new TestString("\u2027"), false, true},
          {new TestString("\u2028"), false, false},
          {new TestString("\u202f"), false, supportSpaces},
          {new TestString("\u2030"), false, true},
          {new TestString("\ud7ff"), false, true},
          {new TestString("\ue000"), false, true},
          {new TestString("\uffef"), false, true},
          {new TestString("\ufff0"), false, false},
          {new TestString("\uffff"), false, false},

          // Standalone high and low surrogates.
          {new TestString("\ud800"), false, false},
          {new TestString("\udbff"), false, false},
          {new TestString("\udc00"), false, false},
          {new TestString("\udfff"), false, false},

          // Single and double code points above 0x10000.
          {new TestString("\ud800\udc00"), true, true},
          {new TestString("\ud800\udcfa"), true, true},
          {new TestString("\ud800\udcfb"), false, true},
          {new TestString("\udbff\udfff"), false, true},
          {new TestString("\ud800\udc00\ud800\udcfa"), true, true},
          {new TestString("\ud800\udc00\udbff\udfff"), false, true}
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
      String artResult =
          runOnArtD8(
              jasminBuilder,
              mainClassName,
              o -> o.setMinApiLevel(ToolHelper.getMinApiLevelForDexVm()));
      assertEquals(expectedResult, artResult);
    } else {
      // Make sure the compiler fails.
      try {
        runOnArtD8(
            jasminBuilder,
            mainClassName,
            o -> o.setMinApiLevel(ToolHelper.getMinApiLevelForDexVm()));
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
              options.setMinApiLevel(ToolHelper.getMinApiLevelForDexVm());
            });
        fail("Art should have failed.");
      } catch (AssertionError e) {
        // Silent on expected failure.
      }
    }
  }
}
