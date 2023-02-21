// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.staticinterfacemethod;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Reproduction for b/196496374. */
@RunWith(Parameterized.class)
public class InlineIntoReprocessedStaticInterfaceMethodTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A");
  }

  static class Main {

    // Field where all writes are removed by the primary optimization pass. All methods that read
    // this field will therefore be enqueued for reprocessing in the second optimization pass.
    public static boolean neverWritten;

    public static void main(String[] args) {
      if (getFalse()) {
        neverWritten = args.length > 0;
      }
      I.method();
    }

    static boolean getFalse() {
      return false;
    }
  }

  static class A {

    static void greet() {
      System.out.println("A");
    }
  }

  interface I {

    // Since this method is moved to I's companion class during the primary optimization pass, we
    // will process the companion method in the second optimization pass, since the body references
    // the `neverWritten` field.
    @NeverInline
    static void method() {
      if (Main.neverWritten) {
        // This will be inlined. When building IR for this method in the second optimization pass,
        // we assert that the outermost caller position is the original signature of the enclosing
        // method. This only holds if we have correctly recorded that the original signature of the
        // companion method I$-CC.method() is I.method().
        System.out.println("Dead...");
      }
      A.greet();
    }
  }
}
