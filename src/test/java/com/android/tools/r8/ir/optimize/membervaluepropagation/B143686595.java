// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.membervaluepropagation;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B143686595 extends TestBase {
  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public B143686595(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult libraryResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(I.class)
            .addKeepClassAndMembersRules(I.class)
            .setMinApi(parameters)
            .compile();

    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addClasspathClasses(I.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(libraryResult.writeToZip())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("0.4");
  }

  interface I {
    float foo(float v);
  }

  static class TestClass {
    static final String UNNECESSARY_PUT;

    static {
      // Will trigger AppInfoWithLiveness#withoutStaticFieldsWrites that should have copied caches
      // of relations between synthesized classes and their super types.
      UNNECESSARY_PUT = "DEAD";
    }

    static final I DOUBLER = t -> t * 2.0f;

    @NeverInline
    static I wrap(I previous, float base) {
      // Interface desugaring
      return t -> previous.foo(t / base);
    }

    public static void main(String... args) {
      I i = wrap(DOUBLER, 10f);
      // Without such consistent cache, this will fail with an assertion error that lower/upper
      // bound types of receiver are mismatching.
      System.out.println(i.foo(2f));
    }
  }
}
