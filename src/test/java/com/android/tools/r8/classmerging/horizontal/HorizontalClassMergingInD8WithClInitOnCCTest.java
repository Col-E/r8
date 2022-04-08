// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a regression test for b/227791663 */
@RunWith(Parameterized.class)
public class HorizontalClassMergingInD8WithClInitOnCCTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    // TODO(b/227791663): Ensure we do not class merge classes with static initializers.
    assumeTrue(
        parameters.getApiLevel().isLessThan(TestBase.apiLevelWithStaticInterfaceMethodsSupport()));
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8(parameters.getBackend())
                .addInnerClasses(getClass())
                .setMode(CompilationMode.RELEASE)
                .setMinApi(parameters.getApiLevel())
                .addOptionsModification(
                    options -> {
                      options.horizontalClassMergerOptions().enable();
                      options.horizontalClassMergerOptions().setRestrictToSynthetics();
                    })
                .compile());
  }

  public interface A {

    int value = Main.getAndIncrement();

    static int getValue() {
      return value;
    }
  }

  public interface B {

    int value = Main.getAndIncrement();

    static int getValue() {
      return value;
    }
  }

  public static class Main {

    private static int value = 0;

    public static int getAndIncrement() {
      return value++;
    }

    public static void main(String[] args) {
      System.out.println(A.getValue());
      System.out.println(B.getValue());
    }
  }
}
