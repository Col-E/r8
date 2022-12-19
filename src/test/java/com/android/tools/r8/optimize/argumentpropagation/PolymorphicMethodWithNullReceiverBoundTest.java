// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * This is an attempt on a regression test for b/250634405. What happens in the input program is
 * that we determine a phi value to be always null in Phi.getDynamicUpperBoundType. That information
 * has not been propagated to the receiver during optimizing of the IR. Therefor the check at {@link
 * ArgumentPropagatorCodeScanner.scan} for receiver always being null returns false.
 *
 * <p>Getting the exact IR to match was difficult so this test short circuit this by disabling IR
 * processing of a simple method (by specifying pass-through) and disabling the check in {@link
 * ArgumentPropagatorCodeScanner.scan}.
 */
@RunWith(Parameterized.class)
public class PolymorphicMethodWithNullReceiverBoundTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  @Test
  public void test() throws Exception {
    // TODO(b/250634405): Check for null in dynamic receiver type.
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(Main.class)
                .enableNoHorizontalClassMergingAnnotations()
                .enableNoVerticalClassMergingAnnotations()
                .setMinApi(parameters.getApiLevel())
                .addOptionsModification(
                    options -> {
                      options.testing.cfByteCodePassThrough =
                          method -> method.getName().startsWith("main");
                      options.testing.checkReceiverAlwaysNullInCallSiteOptimization = false;
                    })
                .compile());
  }

  static class Main {

    public static void main(String[] args) {
      I i = System.currentTimeMillis() > 0 ? new A() : new B();
      i = null;
      i.m();
    }
  }

  public interface I {

    ReturnType m();
  }

  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  static class A implements I {

    @Override
    public ReturnType m() {
      System.out.println("A.m()");
      return new ReturnType();
    }
  }

  static class B implements I {

    @Override
    public ReturnType m() {
      System.out.println("B.m()");
      return new ReturnType();
    }
  }

  @NoHorizontalClassMerging
  static class ReturnType {}
}
