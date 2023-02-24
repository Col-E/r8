// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Regression test code for ensuring that desugared code behaves at least kind of like
 * https://bugs.openjdk.java.net/browse/JDK-8021581
 */
@RunWith(Parameterized.class)
public class DefaultLambdaMethodWithPrivateSuperClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public DefaultLambdaMethodWithPrivateSuperClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  @Test
  public void testDesugar() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isCfRuntime()
                || parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.M),
            r -> r.assertFailureWithErrorThatThrows(IllegalAccessError.class),
            // TODO(b/152199517): Should be illegal access for DEX.
            r -> r.assertSuccessWithOutputLines("interface"));
  }

  interface Named {

    default String name() {
      return "interface";
    }

    class PrivateNameBase {
      private String name() {
        throw new AssertionError("shouldn't get here");
      }
    }

    class PrivateName extends PrivateNameBase implements Named {}
  }

  public static class Main {

    public static void main(String[] args) {
      String unexpected = new Named.PrivateName().name();
      System.out.println(unexpected);
    }
  }
}
