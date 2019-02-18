// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.proguard.rules;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Ignore;
import org.junit.Test;

/** Regression test for b/124584385. */
public class ProguardMatchAllRuleWithPrefixTest extends TestBase {

  @Ignore("b/124584385")
  @Test
  public void test() throws Exception {
    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addProgramClasses(TestClass.class)
            .addKeepRules("-keep class com.android.tools.r8.***")
            .compile()
            .inspector();
    assertThat(inspector.clazz(TestClass.class), isPresent());
  }

  static class TestClass {}
}
