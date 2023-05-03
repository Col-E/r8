// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.invoke;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.examples.ExamplesTestBase;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InvokeTestRunner extends ExamplesTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().enableApiLevelsForCf().build();
  }

  public InvokeTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return Invoke.class;
  }

  @Override
  public List<Class<?>> getTestClasses() {
    return ImmutableList.of(Invoke.class, InvokeInterface.class, SuperClass.class);
  }

  @Override
  public String getExpected() {
    return StringUtils.lines(
        "static0",
        "static1 1",
        "static2 1 2",
        "static3 1 2 3",
        "static4 1 2 3 4",
        "static5 1 2 3 4 5",
        "staticRange 1 2 3 4 5 6",
        "staticDouble0 0.1",
        "staticDouble2 0.1 0.2",
        "staticDoubleRange 0.1 0.2 0.3",
        "direct0",
        "direct1 1",
        "direct2 1 2",
        "direct3 1 2 3",
        "direct4 1 2 3 4",
        "directRange 1 2 3 4 5 6",
        "interface0",
        "interface1 1",
        "interface2 1 2",
        "interface3 1 2 3",
        "interface4 1 2 3 4",
        "interfaceRange 1 2 3 4 5 6",
        "virtual0",
        "virtual1 1",
        "virtual2 1 2",
        "virtual3 1 2 3",
        "virtual4 1 2 3 4",
        "virtualRange 1 2 3 4 5 6",
        "super0",
        "super1 1",
        "super2 1 2",
        "super3 1 2 3",
        "super4 1 2 3 4",
        "superRange 1 2 3 4 5 6",
        "rangeInvoke0 i 0 j 2 d 42.42 e 43.43 l 64424509440",
        "rangeInvoke0 i 0 j 2 d 42.42 e 43.43 l 64424509440",
        "rangeInvoke1 i 0 j 2 d 42.42 e 43.43 l 64424509440",
        "rangeInvoke1 i 0 j 2 d 42.42 e 43.43 l 64424509440",
        "rangeInvoke2 i 0 j 2 d 42.42 e 43.43 l 64424509440",
        "rangeInvoke2 i 0 j 2 d 42.42 e 43.43 l 64424509440",
        "oneArgumentMethod 0",
        "oneArgumentMethod 3",
        "oneArgumentMethod 16",
        "twoArgumentMethod 16 9",
        "twoArgumentMethod 16 10",
        "twoArgumentMethod 16 11",
        "oneDoubleArgumentMethod 1.1",
        "oneDoubleArgumentMethod 4.4",
        "oneDoubleArgumentMethod 16.6",
        "twoDoubleArgumentMethod 16.6 9.9",
        "twoDoubleArgumentMethod 16.6 10.1",
        "twoDoubleArgumentMethod 16.6 11.2",
        "rangeInvokesRepeatedArgument0 0 1 0 1 0 1 0 1",
        "rangeInvokesRepeatedArgument0 0 1 1 1 1 1 1 1",
        "int: 42",
        "double: 42.42",
        "int: 32",
        "double: 32.32",
        "519",
        "15");
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
    runTestDebugComparator();
  }
}
