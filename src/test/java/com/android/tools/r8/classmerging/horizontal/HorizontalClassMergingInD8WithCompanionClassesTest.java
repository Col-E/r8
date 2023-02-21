// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * This is a regression test for b/227791663.
 *
 * <p>Non-sharable synthetics should not be deduplicated or merged in D8. Examples are the global
 * synthetics such as RecordTag and API type stubs, but also fixed-suffix synthetics, such as
 * companion classes for interfaces, which rely on a specific suffix in cases of separate
 * compilation.
 */
@RunWith(Parameterized.class)
public class HorizontalClassMergingInD8WithCompanionClassesTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsEndingAtExcluding(apiLevelWithDefaultInterfaceMethodsSupport())
        .build();
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMode(CompilationMode.RELEASE)
        .setMinApi(parameters)
        .addOptionsModification(
            options -> {
              options.horizontalClassMergerOptions().enable();
              options.horizontalClassMergerOptions().setRestrictToSynthetics();
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("I::foo", "J::bar")
        .inspect(
            inspector -> {
              ClassSubject companionI = inspector.clazz(I.class).toCompanionClass();
              ClassSubject companionJ = inspector.clazz(J.class).toCompanionClass();
              assertThat(companionI, isPresent());
              assertThat(companionJ, isPresent());
              assertNotEquals(companionI.getFinalName(), companionJ.getFinalName());
            });
  }

  public interface I {
    default void foo() {
      System.out.println("I::foo");
    }
  }

  public interface J {
    default void bar() {
      System.out.println("J::bar");
    }
  }

  public static class A implements I {}

  public static class B implements J {}

  public static class Main {

    public static void main(String[] args) {
      new A().foo();
      new B().bar();
    }
  }
}
