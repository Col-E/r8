// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.whyareyounotinlining;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.StringUtils;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WhyAreYouNotInliningInvokeWithUnknownTargetTest extends TestBase {

  private final Backend backend;
  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(Backend.values(), getTestParameters().withNoneRuntime().build());
  }

  public WhyAreYouNotInliningInvokeWithUnknownTargetTest(
      Backend backend, TestParameters parameters) {
    this.backend = backend;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos);
    testForR8(backend)
        .addInnerClasses(WhyAreYouNotInliningInvokeWithUnknownTargetTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-whyareyounotinlining class " + A.class.getTypeName() + " { void m(); }")
        .addOptionsModification(options -> options.testing.whyAreYouNotInliningConsumer = out)
        .enableProguardTestOptions()
        .enableNoHorizontalClassMergingAnnotations()
        .compile();
    out.close();

    assertEquals(
        StringUtils.lines(
            "Method `void "
                + A.class.getTypeName()
                + ".m()` was not inlined into `void "
                + TestClass.class.getTypeName()
                + ".main(java.lang.String[])`: "
                + "could not find a single target."),
        baos.toString());
  }

  static class TestClass {

    public static void main(String[] args) {
      (System.currentTimeMillis() >= 0 ? new A() : new B()).m();
    }
  }

  interface I {

    void m();
  }

  static class A implements I {

    @Override
    public void m() {
      System.out.println("A.m()");
    }
  }

  @NoHorizontalClassMerging
  static class B implements I {

    @Override
    public void m() {
      System.out.println("B.m()");
    }
  }
}
