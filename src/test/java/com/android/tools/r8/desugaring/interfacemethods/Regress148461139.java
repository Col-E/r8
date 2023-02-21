// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress148461139 extends TestBase {
  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public Regress148461139(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    // This only failed if tree-shaking was disabled.
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(Condition.class)
        .noTreeShaking()
        .compile()
        .inspect(i -> assertTrue(i.clazz(Condition.class).isAbstract()));
  }

  public interface Condition {
    boolean isTrue();

    static Condition runOnUiThread() {
      return () -> true;
    }
  }
}
