// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ImpreciseReceiverWithUnknownArgumentInformationWidenedToUnknownTest extends TestBase {

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
                        Reference.methodFromMethod(A.class.getDeclaredMethod("test")))
                    .assertHasBottomMethodState(
                        Reference.methodFromMethod(B.class.getDeclaredMethod("test")))
                    .apply(ignore -> inspected.set()))
        .addVerticallyMergedClassesInspector(
            VerticallyMergedClassesInspector::assertNoClassesMerged)
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A");
    assertTrue(inspected.isTrue());
  }

  static class Main {

    public static void main(String[] args) {
      A aOrB = System.currentTimeMillis() >= 0 ? new A() : new B();
      aOrB.test();
    }
  }

  @NoVerticalClassMerging
  static class A {

    void test() {
      System.out.println("A");
    }
  }

  static class B extends A {

    @Override
    void test() {
      System.out.println("B");
    }
  }
}
