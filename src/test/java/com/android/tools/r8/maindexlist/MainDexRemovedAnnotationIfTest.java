// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// This is a reproduction of b/190623364.
@RunWith(Parameterized.class)
public class MainDexRemovedAnnotationIfTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsEndingAtExcluding(apiLevelWithNativeMultiDexSupport())
        .build();
  }

  public MainDexRemovedAnnotationIfTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testMainDexTracing() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(MainDex.class, Inside.class, Main.class)
        .addKeepClassAndMembersRules(Main.class)
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .addMainDexRules(
            "-if @" + MainDex.class.getTypeName() + " class *", "-keep class <1> { *; }")
        .collectMainDexClasses()
        .compile()
        .inspectMainDexClasses(
            mainDexClasses -> {
              // TODO(b/190623364): Should not be empty.
              assertTrue(mainDexClasses.isEmpty());
            })
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(MainDex.class), not(isPresent()));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  public @interface MainDex {}

  @MainDex
  public static class Inside {

    @NeverInline
    public static void foo() {
      System.out.println("Hello World!");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Inside.foo();
    }
  }
}
