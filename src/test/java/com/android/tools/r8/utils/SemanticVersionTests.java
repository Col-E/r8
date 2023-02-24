// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.SemanticVersion.parse;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SemanticVersionTests extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public SemanticVersionTests(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() {
    assertTrue(parse("1.0.1").isNewerOrEqual(parse("1.0.0")));
    assertFalse(parse("1.0.1").isNewerOrEqual(parse("1.1.0")));
    assertTrue(parse("1.1.0").isNewerOrEqual(parse("1.0.1")));
    assertFalse(parse("1.1.1").isNewerOrEqual(parse("2.0.0")));

    assertTrue(parse("2.0.0").isNewerOrEqual(parse("1.1.1")));
    assertTrue(parse("42.42.42").isNewerOrEqual(parse("9.9.9")));
    assertTrue(parse("9.42.42").isNewerOrEqual(parse("9.9.9")));
    assertTrue(parse("9.9.42").isNewerOrEqual(parse("9.9.9")));

    assertFalse(parse("1.1.1").isNewerOrEqual(parse("2.0.0")));
    assertFalse(parse("9.9.9").isNewerOrEqual(parse("42.42.42")));
    assertFalse(parse("9.9.9").isNewerOrEqual(parse("9.42.42")));
    assertFalse(parse("9.9.9").isNewerOrEqual(parse("9.9.42")));
  }
}
