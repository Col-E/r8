// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.interfacetargets;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MissingInterfaceVirtualTargetsTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("I.foo", "I.foo");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withApiLevel(AndroidApiLevel.B)
        .enableApiLevelsForCf()
        .build();
  }

  public MissingInterfaceVirtualTargetsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Path out =
        testForD8(parameters.getBackend())
            .addProgramClasses(TestClass.class)
            .setMinApi(parameters)
            .compile()
            .assertNoMessages()
            .writeToZip();
    testForD8(parameters.getBackend())
        .addProgramClasses(I.class, A.class)
        .setMinApi(parameters)
        .addRunClasspathFiles(out)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  interface I {
    default void foo() {
      System.out.println("I.foo");
    }
  }

  static class A implements I {
    // No override.
  }

  static class TestClass {

    static void targetI(I i) {
      // This invoke-interface target should not cause missing class warnings.
      i.foo();
    }

    public static void main(String[] args) {
      A a = new A();
      targetI(a);
      // This invoke-virtual target should not cause missing class warnings.
      a.foo();
    }
  }
}
