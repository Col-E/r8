// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.constants;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.examples.ExamplesTestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ConstantsTestRunner extends ExamplesTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().enableApiLevelsForCf().build();
  }

  public ConstantsTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return Constants.class;
  }

  @Override
  public String getExpected() {
    return "-8-1017-32768-9832767-65536-26843545625165824015728640983040-214748364865536-3276932768"
        + "-65535-26843545525165824115728641983041-214748364765537-32768-10132767-32769"
        + "-2147483648214748364732768-281474976710656"
        + "-11529215046068469761080863910568919040675539944105574404222124650659840"
        + "-92233720368547758082814749767106569223090561878065152-21474836492147483648"
        + "-140737488355329-281474976710655"
        + "-11529215046068469751080863910568919041675539944105574414222124650659841"
        + "-922337203685477580728147497671065792233720368547758079223090561878065153";
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
