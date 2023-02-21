// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.ProgramMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class HashCodeTest extends TestBase {

  private static final Class<?> MAIN = TestClass.class;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(HashCodeTest.class)
        .addKeepMainRule(MAIN)
        .enableNoVerticalClassMergingAnnotations()
        .addOptionsModification(
            o ->
                o.testing.callSiteOptimizationInfoInspector = this::callSiteOptimizationInfoInspect)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("10");
  }

  private void callSiteOptimizationInfoInspect(ProgramMethod method) {
    // TODO(b/139246447): should avoid visiting A#<init>, which is trivial, default init!
    assert method.getHolderType().toSourceString().endsWith("A")
            && method.toSourceString().contains("<init>")
        : "Unexpected revisit: " + method.toSourceString();
  }

  static class TestClass {
    public static void main(String[] args) {
      A obj = System.currentTimeMillis() > 0 ? new B() : new C();
      System.out.println(obj.hashCode());
    }
  }

  @NoVerticalClassMerging
  static class A {
    @Override
    public int hashCode() {
      return 1;
    }
  }

  @NoVerticalClassMerging
  static class B extends A {
    @Override
    public int hashCode() {
      return super.hashCode() * 7 + 3;
    }
  }

  static class C extends B {
    @Override
    public int hashCode() {
      return super.hashCode() * 31 + 17;
    }
  }
}
