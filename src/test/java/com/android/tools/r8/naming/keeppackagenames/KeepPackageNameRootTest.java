// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.keeppackagenames;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestShrinkerBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepPackageNameRootTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public KeepPackageNameRootTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8Compat() throws Exception {
    run(testForR8Compat(Backend.CF), false);
  }

  @Test
  public void testR8Full() throws Exception {
    run(testForR8(Backend.CF), true);
  }

  @Test
  public void testR8PG() throws Exception {
    run(testForProguard(ProguardVersion.V7_0_0).addKeepRules("-dontwarn"), false);
  }

  private void run(TestShrinkerBuilder<?, ?, ?, ?, ?> testBuilder, boolean isFullMode)
      throws Exception {
    testBuilder
        .addProgramClassFileData(
            transformer(Main.class)
                .setClassDescriptor("Lfoo/Main;")
                .removeInnerClasses()
                .transform())
        .addKeepRules("-keeppackagenames foo.**")
        .addKeepClassRulesWithAllowObfuscation("foo.Main")
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              assertEquals(1, inspector.allClasses().size());
              inspector.forAllClasses(
                  clazz -> {
                    if (isFullMode) {
                      assertEquals("foo", clazz.getDexProgramClass().getType().getPackageName());
                    } else {
                      assertNotEquals("foo", clazz.getDexProgramClass().getType().getPackageName());
                    }
                  });
            });
  }

  /* Will be in package foo */
  public static class Main {}
}
