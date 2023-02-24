// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class CfVersionTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public CfVersionTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() {
    CfVersion v1_1 = CfVersion.V1_1;
    assertEquals(Opcodes.V1_1, v1_1.raw());
    assertEquals(45, v1_1.major());
    assertEquals(3, v1_1.minor());

    CfVersion v1_2 = CfVersion.V1_2;
    assertEquals(Opcodes.V1_2, v1_2.raw());
    assertEquals(46, v1_2.major());
    assertEquals(0, v1_2.minor());

    CfVersion v9 = CfVersion.V9;
    assertEquals(Opcodes.V9, v9.raw());
    assertEquals(53, v9.major());
    assertEquals(0, v9.minor());

    assertLessThan(v1_1, v1_2);
    assertLessThan(v1_2, v9);
  }

  private static void assertLessThan(CfVersion less, CfVersion more) {
    assertFalse(less.isEqualTo(more));
    assertEquals(-1, less.compareTo(more));
    assertEquals(1, more.compareTo(less));
    assertTrue(less.isLessThan(more));
    assertTrue(less.isLessThanOrEqualTo(more));
    assertFalse(less.isGreaterThan(more));
    assertFalse(less.isGreaterThanOrEqualTo(more));
    assertFalse(more.isLessThan(less));
    assertFalse(more.isLessThanOrEqualTo(less));
    assertTrue(more.isGreaterThan(less));
    assertTrue(more.isGreaterThanOrEqualTo(less));
  }
}
