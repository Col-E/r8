// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a regression test for b/286781273 */
@RunWith(Parameterized.class)
public class SameLineMappingTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    IntBox box = new IntBox(0);
    MethodReference main =
        Reference.methodFromMethod(Main.class.getDeclaredMethod("main", String[].class));
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(Main.class)
                .setPredictiveLineNumbering(
                    (context, line) -> {
                      if (context.getReference().equals(main)) {
                        return (box.getAndIncrement() % 2) + 1;
                      }
                      return line;
                    })
                .transform())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addKeepAttributeLineNumberTable()
        .addOptionsModification(
            options -> options.lineNumberOptimization = LineNumberOptimization.OFF)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!", "Hello World!");
  }

  public static class Main {

    public static void main(String[] args) {
      String val1 = foo();
      canThrow(val1);
      String val2 = foo();
      canThrow(val2);
    }

    @NeverInline
    private static String foo() {
      if (System.currentTimeMillis() == 0) {
        throw new RuntimeException("Hello World");
      }
      return "Hello World!";
    }

    @NeverInline
    private static void canThrow(String str) {
      if (System.currentTimeMillis() == 0) {
        throw new RuntimeException(str);
      }
      System.out.println(str);
    }
  }
}
