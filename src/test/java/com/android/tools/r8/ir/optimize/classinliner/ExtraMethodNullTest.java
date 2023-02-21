// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ExtraMethodNullTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ExtraMethodNullTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(One.class)
        .addKeepMainRule(One.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), One.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class);
  }

  public static class One {

    public static void main(String[] args) {
      One one = new One();
      Other other = args.length == 0 ? null : new Other();
      other.print(one);
    }

    static class Other {
      Object print(Object one) {
        return one;
      }
    }
  }
}
