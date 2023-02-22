// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class JunitAvailabilityInHostArtTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public JunitAvailabilityInHostArtTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  // It of the Art host VMs only 5.1.1 and 6.0.1 have junit.framework.Assert present at runtime.
  private void checkResult(TestRunResult<?> result) {
    if (parameters.getRuntime().isDex()
        && ((parameters.getRuntime().asDex().getVm() == DexVm.ART_6_0_1_HOST)
            || (parameters.getRuntime().asDex().getVm() == DexVm.ART_5_1_1_HOST))) {
      result.assertSuccessWithOutput(StringUtils.lines("class junit.framework.Assert"));
    } else {
      result.assertFailureWithErrorThatMatches(containsString("ClassNotFoundException"));
    }
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(JunitAvailabilityInHostArtTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkResult);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.getBackend() == Backend.DEX);
    testForD8()
        .addInnerClasses(JunitAvailabilityInHostArtTest.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::checkResult);
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      System.out.println(Class.forName("junit.framework.Assert"));
    }
  }

  static class A {}
}
