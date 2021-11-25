// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.keep;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ImplementsMissingClassDirectTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testR8() throws Exception {
    run(testForR8(Backend.CF).allowUnusedProguardConfigurationRules());
  }

  @Test
  public void testProguard() throws Exception {
    run(
        testForProguard(ProguardVersion.V7_0_0)
            .addDontWarn(MissingInterface.class)
            .addDontWarn(ImplementsMissingClassDirectTest.class));
  }

  private <T extends TestShrinkerBuilder<?, ?, ?, ?, T>> void run(T builder) throws Exception {
    builder
        .addProgramClasses(Main.class, A.class)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
        .addKeepMainRule(Main.class)
        .addKeepRules("-keep class ** implements " + MissingInterface.class.getTypeName())
        .compile()
        .inspect(
            inspector ->
                assertEquals(
                    ImmutableList.of(inspector.clazz(Main.class)), inspector.allClasses()));
  }

  interface MissingInterface {}

  static class A implements MissingInterface {}

  static class Main {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
