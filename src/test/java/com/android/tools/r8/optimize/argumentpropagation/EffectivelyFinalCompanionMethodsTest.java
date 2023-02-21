// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isStatic;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EffectivelyFinalCompanionMethodsTest extends TestBase {

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
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject hostClassSubject = inspector.clazz(Host.class);
              assertThat(hostClassSubject, isAbsent());

              ClassSubject companionClassSubject = inspector.clazz(Host.Companion.class);
              assertThat(companionClassSubject, isPresent());
              assertEquals(4, companionClassSubject.allMethods().size());
              assertThat(companionClassSubject.uniqueMethodWithOriginalName("foo"), isStatic());
              assertThat(companionClassSubject.uniqueMethodWithOriginalName("bar"), isStatic());
              assertThat(companionClassSubject.uniqueMethodWithOriginalName("baz"), isStatic());
              assertThat(companionClassSubject.uniqueMethodWithOriginalName("qux"), isStatic());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Foo!", "Bar!", "Baz!", "Qux!", "Baz!");
  }

  static class Main {

    public static void main(String[] args) {
      Host.companion.foo();
    }
  }

  static class Host {

    static final Companion companion = new Companion();

    @NeverClassInline
    @NoHorizontalClassMerging
    static class Companion {

      @NeverInline
      void foo() {
        System.out.println("Foo!");
        bar();
      }

      @NeverInline
      void bar() {
        System.out.println("Bar!");
        baz(true);
      }

      @NeverInline
      void baz(boolean doQux) {
        System.out.println("Baz!");
        if (doQux) {
          qux();
        }
      }

      @NeverInline
      void qux() {
        System.out.println("Qux!");
        baz(false);
      }
    }
  }
}
