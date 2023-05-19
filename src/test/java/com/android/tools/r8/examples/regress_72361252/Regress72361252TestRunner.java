// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.regress_72361252;

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
public class Regress72361252TestRunner extends ExamplesTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().enableApiLevelsForCf().build();
  }

  public Regress72361252TestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return TestClass.class;
  }

  @Override
  public List<Class<?>> getTestClasses() throws Exception {
    String outerClass = typeName(TestClass.class);
    return ImmutableList.of(
        getMainClass(),
        Class.forName(outerClass + "$1"),
        Class.forName(outerClass + "$X"),
        Class.forName(outerClass + "$A"),
        Class.forName(outerClass + "$B"),
        Class.forName(outerClass + "$C"));
  }

  @Override
  public String getExpected() {
    return StringUtils.lines(
        "An exception was caught.",
        "r  = -4.37780077E8",
        "mZ = false",
        "mI = 0",
        "mJ = 0",
        "mF = 0.0",
        "mD = 0.0",
        "mArray = [[[[[[[[[1.7861851E9]]]]]]]]]");
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
