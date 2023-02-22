// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestedStringBuilderTest extends TestBase {
  private static final Class<?> MAIN = NestedStringBuilders.class;
  private static final String EXPECTED = StringUtils.lines("one$two");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void b113859361() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(MAIN)
        .addKeepMainRule(MAIN)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN.getTypeName(), "$")
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            codeInspector -> {
              ClassSubject mainClass = codeInspector.clazz(MAIN);
              MethodSubject main = mainClass.uniqueMethod();
              assertEquals(
                  // TODO(b/113859361): should be 1 after merging StringBuilder's
                  2,
                  main.streamInstructions()
                      .filter(i -> i.isNewInstance(StringBuilder.class.getTypeName()))
                      .count());
            });
  }

  static class NestedStringBuilders {

    public static void main(String... args) {
      System.out.println(concat("one", args[0]) + "two");
    }

    public static String concat(String one, String two) {
      return one + two;
    }
  }
}