// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.proguard.configuration;

import com.android.tools.r8.TestBase;
import org.junit.Test;

public class UnusedKeepRuleTest extends TestBase {

  @Test
  public void test() throws Exception {
    testForR8(Backend.DEX)
        .addKeepRules("-keep class NotPresent")
        .allowUnusedProguardConfigurationRules()
        .compile()
        .assertInfosCount(1);
  }
}
