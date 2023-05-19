// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class HorizontalClassMergingOfInitArgumentTypesTest extends TestBase {

  @Parameter(0)
  public boolean enableHorizontalClassMerging;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, horizontal class merging: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> {
              options.callSiteOptimizationOptions().setForceSyntheticsForInstanceInitializers(true);
              options.horizontalClassMergerOptions().enableIf(enableHorizontalClassMerging);
            })
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              int expectedNumberOfSynthetics =
                  2 - BooleanUtils.intValue(enableHorizontalClassMerging);
              assertEquals(3 + expectedNumberOfSynthetics, inspector.allClasses().size());
              assertThat(inspector.clazz(Main.class), isPresent());
              assertThat(inspector.clazz(A.class), isPresent());
              assertThat(inspector.clazz(B.class), isPresent());
              assertEquals(
                  expectedNumberOfSynthetics,
                  inspector.allClasses().stream()
                      .filter(
                          clazz ->
                              SyntheticItemsTestUtils.isExternalNonFixedInitializerTypeArgument(
                                  clazz.getOriginalReference()))
                      .count());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A()", "A(Object)", "B()", "B(Object)");
  }

  static class Main {

    public static void main(String[] args) {
      String arg = args.length > 0 ? args[1] : null;
      System.out.println(new A());
      System.out.println(new A(arg));
      System.out.println(new B());
      System.out.println(new B(arg));
    }
  }

  @NoHorizontalClassMerging
  static class A {

    boolean unused;

    @NeverInline
    A() {}

    // Unused argument removal will rewrite this into A(A$$ExternalSynthetic$IA0)
    @NeverInline
    A(Object unused) {
      this.unused = true;
    }

    @Override
    public String toString() {
      return unused ? "A(Object)" : "A()";
    }
  }

  @NoHorizontalClassMerging
  static class B {

    boolean unused;

    @NeverInline
    B() {}

    // Unused argument removal will rewrite this into B(B$$ExternalSynthetic$IA0)
    @NeverInline
    B(Object unused) {
      this.unused = true;
    }

    @Override
    public String toString() {
      return unused ? "B(Object)" : "B()";
    }
  }
}
