// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.switchmaps;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.examples.ExamplesTestBase;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SwitchMapsTestRunner extends ExamplesTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().enableApiLevelsForCf().build();
  }

  public SwitchMapsTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return Switches.class;
  }

  @Override
  public List<Class<?>> getTestClasses() throws Exception {
    return ImmutableList.of(
        getMainClass(),
        Class.forName(getMainClass().getTypeName() + "$1"),
        Days.class,
        Colors.class);
  }

  @Override
  public String getExpected() {
    return StringUtils.lines(
        "other",
        "1, 3 or 4",
        "2 or 5",
        "other",
        "2 or 5",
        "3 or 5",
        "1, 3 or 4",
        "2 or 5",
        "other",
        "1, 3 or 4",
        "2 or 5",
        "3 or 5",
        "2 or 5",
        "other",
        "6",
        "7",
        "7",
        "rar",
        "colorful",
        "blew",
        "colorful",
        "soylent",
        "sooo green",
        "fifty",
        "not really");
  }

  @Test
  public void testDesugaring() throws Exception {
    runTestDesugaring();
  }

  @Test
  public void testR8() throws Exception {
    runTestR8();
  }

  @Test
  public void testDebug() throws Exception {
    // TODO(b/79671093): DEX has different line number info during stepping.
    Assume.assumeTrue(parameters.isCfRuntime());
    runTestDebugComparator();
  }
}
