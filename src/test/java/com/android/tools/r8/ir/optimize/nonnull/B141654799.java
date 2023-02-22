// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.nonnull;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
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
public class B141654799 extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(B141654799.class)
        .enableInliningAnnotations()
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("The end")
        .inspect(
            inspector -> {
              ClassSubject main = inspector.clazz(TestClass.class);
              assertThat(main, isPresent());
              MethodSubject mainMethod = main.mainMethod();
              assertThat(mainMethod, isPresent());
              assertTrue(
                  mainMethod.streamInstructions().noneMatch(i -> i.isIfEqz() || i.isIfNez()));
            });
  }

  static class TestClass {
    public static void main(String... args) {
      TestClass x = null;
      if (System.currentTimeMillis() > 0) {
        TestClass y = (TestClass) returnsNull();
        if (y != null) {
          x = y;
        }
      }
      if (x != null) {
        System.out.println("Dead code: " + x.toString());
      }
      System.out.println("The end");
    }

    @NeverInline
    static Object returnsNull() {
      return null;
    }
  }
}
