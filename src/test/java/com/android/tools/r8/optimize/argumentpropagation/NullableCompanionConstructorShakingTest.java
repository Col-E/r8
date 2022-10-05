// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isStatic;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NullableCompanionConstructorShakingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(options -> options.enableClassStaticizer = false)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              MethodSubject companionMethodSubject =
                  inspector.clazz(Host.Companion.class).uniqueMethodWithOriginalName("greet");
              assertThat(companionMethodSubject, isStatic());

              MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
              assertEquals(
                  2,
                  mainMethodSubject
                      .streamInstructions()
                      .filter(InstructionSubject::isInvoke)
                      .count());
              assertThat(mainMethodSubject, invokesMethodWithName("getClass"));
              assertThat(mainMethodSubject, invokesMethod(companionMethodSubject));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(NullPointerException.class);
  }

  static class Main {

    public static void main(String[] args) {
      // This method call will be staticized and a null check should be inserted for Host.companion.
      Host.companion.greet();
    }
  }

  static class Host {

    static final Companion companion = System.currentTimeMillis() >= 0 ? null : new Companion();

    @NeverClassInline
    @NoHorizontalClassMerging
    static class Companion {

      @NeverInline
      void greet() {
        System.out.print("Hello world!");
      }
    }
  }
}
