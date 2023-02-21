// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// This is a regression test for b/235184674.
@RunWith(Parameterized.class)
public class ApiModelBridgeToLibraryMethodTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("8");

  @Test()
  public void testR8WithApiLevelCheck() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .setMinApi(parameters)
        .addKeepMainRule(TestClassWithApiLevelCheck.class)
        .addAndroidBuildVersion()
        .run(parameters.getRuntime(), TestClassWithApiLevelCheck.class)
        .applyIf(
            parameters.isCfRuntime() || parameters.getApiLevel().isLessThan(AndroidApiLevel.N),
            r -> r.assertSuccessWithOutputLines("No call"),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  static class TestClassWithApiLevelCheck {

    private static void m(B b) {
      System.out.println(b.compose(b).apply(2));
    }

    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= 24) {
        m(new B());
      } else {
        System.out.println("No call");
      }
    }
  }

  interface MyFunction<V, R> extends Function<V, R> {}

  static class B implements MyFunction<Integer, Integer> {

    @Override
    public <V> Function<V, Integer> compose(Function<? super V, ? extends Integer> before) {
      return MyFunction.super.compose(before);
    }

    @Override
    public Integer apply(Integer integer) {
      return integer * 2;
    }
  }
}
