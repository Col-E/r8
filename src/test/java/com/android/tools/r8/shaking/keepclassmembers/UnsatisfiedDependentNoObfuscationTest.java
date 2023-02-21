// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.keepclassmembers;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnsatisfiedDependentNoObfuscationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            // This rule should not have any impact on the compilation, since GreeterConsumer is
            // dead.
            "-keepclassmembers,includedescriptorclasses class "
                + GreeterConsumer.class.getTypeName()
                + " {",
            "  <methods>;",
            "}")
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> assertThat(inspector.clazz(Greeter.class), isPresentAndRenamed()))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello");
  }

  static class Main {
    public static void main(String[] args) {
      Greeter.hello();
    }
  }

  static class Greeter {
    @NeverInline
    static void hello() {
      System.out.println("Hello");
    }
  }

  static class GreeterConsumer {
    void accept(Greeter greeter) {}
  }
}
