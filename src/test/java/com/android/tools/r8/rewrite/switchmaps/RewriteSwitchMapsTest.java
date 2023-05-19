// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.switchmaps;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.examples.switchmaps.Colors;
import com.android.tools.r8.examples.switchmaps.Days;
import com.android.tools.r8.examples.switchmaps.SwitchMapsTestRunner;
import com.android.tools.r8.examples.switchmaps.Switches;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RewriteSwitchMapsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}, {1}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  private static final String SWITCHMAP_CLASS_NAME = typeName(Switches.class) + "$1";

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClassesAndInnerClasses(Switches.class)
        .addProgramClasses(Colors.class, Days.class)
        .run(parameters.getRuntime(), Switches.class)
        .assertSuccessWithOutput(SwitchMapsTestRunner.EXPECTED)
        .inspect(inspector -> assertThat(inspector.clazz(SWITCHMAP_CLASS_NAME), isPresent()));
  }

  @Test
  public void checkSwitchMapsRemoved() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(Switches.class)
        .addProgramClasses(Colors.class, Days.class)
        .addKeepMainRule(Switches.class)
        .addDontObfuscate()
        .addKeepAllAttributes()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Switches.class)
        .assertSuccessWithOutput(SwitchMapsTestRunner.EXPECTED)
        .inspect(inspector -> assertThat(inspector.clazz(SWITCHMAP_CLASS_NAME), isAbsent()));
  }
}
