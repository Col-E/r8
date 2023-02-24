// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AndroidJarPresenceTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public AndroidJarPresenceTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() {
    for (AndroidApiLevel apiLevel : AndroidApiLevel.values()) {
      assertEquals(ToolHelper.shouldHaveAndroidJar(apiLevel), ToolHelper.hasAndroidJar(apiLevel));
    }
  }
}
