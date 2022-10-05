// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.redundantfieldloadelimination;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
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
public class RedundantInitClassBeforeInvokeStaticTest extends TestBase {

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
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject greeterClassSubject = inspector.clazz(Greeter.class);
              assertThat(greeterClassSubject, isPresent());
              assertThat(greeterClassSubject.uniqueMethodWithOriginalName("hello"), isAbsent());
              assertThat(greeterClassSubject.uniqueMethodWithOriginalName("world"), isPresent());
              assertEquals(0, greeterClassSubject.allFields().size());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class Main {

    public static void main(String[] args) {
      // The inlining of hello() will materialize an init-class instruction for Greeter. This
      // instruction should be removed since it immediately precedes another instruction that
      // initializes Greeter.
      Greeter.hello();
      Greeter.world();
    }
  }

  static class Greeter {

    static {
      System.out.print("Hello");
    }

    static void hello() {}

    @NeverInline
    static void world() {
      System.out.println(" world!");
    }
  }
}
