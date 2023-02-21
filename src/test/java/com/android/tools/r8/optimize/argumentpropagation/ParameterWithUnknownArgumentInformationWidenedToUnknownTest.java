// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.BooleanBox;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ParameterWithUnknownArgumentInformationWidenedToUnknownTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    BooleanBox inspected = new BooleanBox();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addArgumentPropagatorCodeScannerResultInspector(
            inspector ->
                inspector
                    .assertHasUnknownMethodState(
                        Reference.methodFromMethod(Main.class.getDeclaredMethod("test", A.class)))
                    .apply(ignore -> inspected.set()))
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(null, "A");
    assertTrue(inspected.isTrue());
  }

  static class Main {

    public static void main(String[] args) {
      A alwaysNull = null;
      A neverNull = new A();
      test(alwaysNull);
      test(neverNull);
    }

    @NeverInline
    static void test(A a) {
      System.out.println(a);
    }
  }

  static class A {

    @Override
    public String toString() {
      return "A";
    }
  }
}
