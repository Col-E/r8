// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DexVersionUnitTests extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public DexVersionUnitTests(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testIntValue() {
    assertFalse(DexVersion.getDexVersion(34).isPresent());
    assertEquals(DexVersion.V35, DexVersion.getDexVersion(35).get());
    assertEquals(DexVersion.V39, DexVersion.getDexVersion(39).get());
    assertFalse(DexVersion.getDexVersion(999).isPresent());
  }

  @Test
  public void testCharValues() {
    assertFalse(DexVersion.getDexVersion('0', '3', '4').isPresent());
    assertEquals(DexVersion.V35, DexVersion.getDexVersion('0', '3', '5').get());
    assertEquals(DexVersion.V39, DexVersion.getDexVersion('0', '3', '9').get());
    assertFalse(DexVersion.getDexVersion('9', '9', '9').isPresent());
  }
}