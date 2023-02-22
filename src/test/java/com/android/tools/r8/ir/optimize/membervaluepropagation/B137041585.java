// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B137041585 extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(B137041585.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(R.font.class);
              assertThat(classSubject, isPresent());
              assertThat(
                  classSubject.uniqueFieldWithOriginalName("roboto_mono_bold"), not(isPresent()));
              assertThat(
                  classSubject.uniqueFieldWithOriginalName("roboto_mono_regular"),
                  not(isPresent()));
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("1", "2", "1", "2", "1");
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(R.font.roboto_mono_bold);
      System.out.println(R.font.roboto_mono_regular);
      System.out.println(R.font.roboto_mono_weights[0]);
      System.out.println(R.font.roboto_mono_weights[1]);
      System.out.println(R.font.roboto_mono_weights2[0]);
    }
  }

  static class R {

    static class font {

      static int roboto_mono_bold = 1;
      static int roboto_mono_regular = 2;
      // Uses NewArrayFilledData to populate.
      static int[] roboto_mono_weights = {roboto_mono_bold, roboto_mono_regular};
      // Uses ArrayPut to populate.
      static int[] roboto_mono_weights2 = {roboto_mono_bold};
    }
  }
}
