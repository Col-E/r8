// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
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
    // TODO(b/235184674): Run on all runtimes.
    return getTestParameters().withCfRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Test()
  public void testR8WithApiLevelCheck() {
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
                .setMinApi(parameters.getApiLevel())
                .addKeepMainRule(TestClassWithApiLevelCheck.class)
                .addAndroidBuildVersion()
                .compileWithExpectedDiagnostics(
                    diagnostics -> {
                      // TODO(b/235184674): Should not throw with an error.
                      diagnostics.assertErrorMessageThatMatches(
                          containsString(
                              "Unexpected virtual method without library method override"
                                  + " information"));
                    }));
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
      return null;
    }
  }
}
