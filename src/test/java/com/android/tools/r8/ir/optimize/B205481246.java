// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B205481246 extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isDexRuntimeVersion(Version.V6_0_1)
                && parameters.getApiLevel().isEqualTo(AndroidApiLevel.B),
            runResult ->
                runResult.assertFailureWithErrorThatMatches(
                    containsString("Check failed: receiver != nullptr virtual")),
            runResult ->
                runResult
                    .assertFailureWithErrorThatThrows(NullPointerException.class)
                    .assertStdoutMatches(equalTo("")));
  }

  static class Main {

    public static void main(String[] args) {
      Foo alwaysNull = System.currentTimeMillis() > 0 ? null : new Foo();
      try {
        if (alwaysNull.alwaysTrue()) {
          System.out.println("true");
        }
      } catch (NullPointerException expected) {
        Objects.requireNonNull(alwaysNull);
      }
    }

    static class Foo {

      boolean alwaysTrue = true;

      @NeverInline
      boolean alwaysTrue() {
        return alwaysTrue;
      }
    }
  }
}
