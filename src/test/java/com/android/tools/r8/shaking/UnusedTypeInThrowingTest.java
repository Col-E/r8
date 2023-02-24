// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedTypeInThrowingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testTypeIsMarkedAsLive() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addKeepMainRule(Main.class)
            .addKeepAttributeExceptions()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), Main.class)
            .assertSuccessWithOutput(Main.EXPECTED)
            .inspector();

    assertThat(inspector.clazz(UnusedThrowable.class), isPresent());
    // TODO(b/124217402) When done check that THROWABLE_CLASS is kept by the throwing annotation.
  }

  static class Main {

    public static final String EXPECTED = System.currentTimeMillis() >= 0 ? "42" : null;

    public static void main(String[] args) throws UnusedThrowable {
      System.out.print(EXPECTED);
    }
  }

  static class UnusedThrowable extends Throwable {}
}
