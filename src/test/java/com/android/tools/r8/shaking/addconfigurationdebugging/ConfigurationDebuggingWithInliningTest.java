// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.addconfigurationdebugging;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a reproduction of b/298965633. */
@RunWith(Parameterized.class)
public class ConfigurationDebuggingWithInliningTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testForR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, Bar.class)
        .addKeepRules("-addconfigurationdebugging")
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        // AddConfigurationDebugging will insert a call to android.util.log.
        .applyIf(
            parameters.isDexRuntime(),
            result ->
                result
                    .assertFailureWithErrorThatThrows(NoClassDefFoundError.class)
                    .assertFailureWithErrorThatMatches(containsString("Landroid/util/Log;")))
        .applyIf(
            parameters.isCfRuntime(),
            result ->
                result.assertFailureWithErrorThatMatches(
                    containsString("Missing method in " + typeName(Bar.class))));
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      System.out.println("Hello World");
      callM();
    }

    private static String getObjectName() {
      return new StringBuilder()
          .append("com.android.tools.r8.shaking.addconfigurationdebugging")
          .append(".ConfigurationDebuggingWithInliningTest$Bar")
          .toString();
    }

    @NeverInline
    private static void callM() {
      try {
        Bar bar = (Bar) Class.forName(getObjectName()).newInstance();
        bar.print();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static class Bar {

    public void print() {
      System.out.println("Initialized");
    }
  }
}
