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
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CompanionConstructorShakingTest extends TestBase {

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
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject hostClassSubject = inspector.clazz(Host.class);
              assertThat(hostClassSubject, isPresent());
              assertEquals(1, hostClassSubject.allMethods().size());
              assertThat(hostClassSubject.clinit(), isAbsent());
              assertThat(hostClassSubject.uniqueMethodWithOriginalName("keepHost"), isPresent());

              ClassSubject companionClassSubject = inspector.clazz(Host.Companion.class);
              assertThat(companionClassSubject, isPresent());
              assertEquals(1, companionClassSubject.allMethods().size());
              assertThat(companionClassSubject.init(), isAbsent());

              MethodSubject greetMethodSubject =
                  companionClassSubject.uniqueMethodWithOriginalName("greet");
              assertThat(greetMethodSubject, isStatic());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class Main {

    public static void main(String[] args) {
      Host.companion.greet();
      Host.keepHost();
    }
  }

  static class Host {

    static final Companion companion = new Companion();

    @NeverInline
    static void keepHost() {
      System.out.println();
    }

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
