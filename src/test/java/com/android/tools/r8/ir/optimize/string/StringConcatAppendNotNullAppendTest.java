// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.ir.optimize.string.utils.StringBuilderCodeMatchers.countStringBuilderAppends;
import static com.android.tools.r8.ir.optimize.string.utils.StringBuilderCodeMatchers.countStringBuilderInits;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringConcatAppendNotNullAppendTest extends TestBase {

  private static final String EXPECTED = "ok";

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(
            inspect -> {
              MethodSubject methodSubject = inspect.clazz(Main.class).mainMethod();
              assertThat(methodSubject, isPresent());
              // TODO(b/129200243): See if this can be arg1.concat(String.valueOf(arg2))
              assertEquals(1, countStringBuilderInits(methodSubject.asFoundMethodSubject()));
              assertEquals(2, countStringBuilderAppends(methodSubject.asFoundMethodSubject()));
            });
  }

  public static class Main {

    public static void main(String[] args) {
      String arg1 = System.currentTimeMillis() > 0 ? "o" : "";
      String arg2 = System.currentTimeMillis() > 0 ? "k" : null;
      System.out.println(new StringBuilder().append(arg1).append(arg2).toString());
    }
  }
}
