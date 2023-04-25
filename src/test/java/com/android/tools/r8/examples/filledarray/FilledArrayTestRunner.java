// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.filledarray;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.examples.ExamplesTestBase;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FilledArrayTestRunner extends ExamplesTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().enableApiLevelsForCf().build();
  }

  public FilledArrayTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return FilledArray.class;
  }

  @Override
  public String getExpected() {
    return StringUtils.lines(
        "booleans",
        "true",
        "true",
        "false",
        "false",
        "false",
        "true",
        "false",
        "false",
        "bytes",
        "0",
        "1",
        "2",
        "3",
        "4",
        "5",
        "6",
        "7",
        "8",
        "9",
        "10",
        "11",
        "12",
        "13",
        "14",
        "15",
        "16",
        "17",
        "18",
        "-19",
        "-20",
        "-96",
        "127",
        "-128",
        "21",
        "22",
        "-23",
        "chars",
        "a",
        "b",
        "c",
        "d",
        "a",
        "b",
        "c",
        "d",
        "ints",
        "2147483647",
        "0",
        "-42",
        "42",
        "-2147483648",
        "2147483647",
        "0",
        "-42",
        "42",
        "-2147483648",
        "shorts",
        "32767",
        "0",
        "-42",
        "42",
        "-32768",
        "32767",
        "0",
        "-42",
        "42",
        "-32768",
        "longs",
        "9223372036854775807",
        "1311693406324658740",
        "-1311693406324658740",
        "-9223372036854775808",
        "1311747200790041140",
        "-1311693406324671263",
        "floats",
        "3.4028235E38",
        "23.23",
        "-43.123",
        "1.4E-45",
        "1.1754944E-38",
        "23.23",
        "-43.123",
        "doubles",
        "1.7976931348623157E308",
        "1.23123123123E8",
        "-43333.123",
        "4.9E-324",
        "2.2250738585072014E-308",
        "1.23123123123E8",
        "-43333.123",
        "i = 1",
        "ints = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]",
        "ints2 = [0, 1, 2, 3, 4]",
        "i = 7",
        "ints = [0, 1, 2, 3, 4]",
        "Exception: class java.lang.ArithmeticException",
        "Exception: class java.lang.ArithmeticException");
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
