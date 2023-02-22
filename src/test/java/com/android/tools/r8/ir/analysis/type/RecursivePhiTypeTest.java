// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KeepConstantArguments;
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
public class RecursivePhiTypeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(RecursivePhiTypeTest.class)
        .addKeepMainRule(TestClass.class)
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject greeterSubject = inspector.clazz(Greeter.class);
              assertThat(greeterSubject, not(isPresent()));

              ClassSubject greeterImplSubject = inspector.clazz(GreeterImpl.class);
              assertThat(greeterImplSubject, isPresent());
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class TestClass {

    public static void main(String[] args) {
      getGreeter(null).greet();
    }

    @KeepConstantArguments
    static Greeter getGreeter(Greeter result) {
      while (!(result instanceof GreeterImpl)) {
        if (System.currentTimeMillis() >= 0) {
          result = create();
        }
      }
      return result;
    }

    @NeverInline
    static Greeter create() {
      return new GreeterImpl();
    }
  }

  static class Greeter {

    public void greet() {
      throw new RuntimeException("Unreachable");
    }
  }

  static class GreeterImpl extends Greeter {

    @Override
    public void greet() {
      System.out.println("Hello world!");
    }
  }
}
