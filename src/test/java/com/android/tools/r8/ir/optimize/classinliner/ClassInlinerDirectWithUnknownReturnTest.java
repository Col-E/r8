// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverSingleCallerInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassInlinerDirectWithUnknownReturnTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInlinerDirectWithUnknownReturnTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test(expected = CompilationFailedException.class)
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableNeverSingleCallerInlineAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertErrorsMatch(
                  diagnosticMessage(
                      containsString("Unexpected values live at entry to first block: [v1]")));
            });
  }

  public static class A {

    public int number;

    @NeverSingleCallerInline
    public A abs() {
      if (number > 0) {
        return this;
      }
      return new A();
    }

    @Override
    @NeverSingleCallerInline
    public String toString() {
      return "Hello World " + number;
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A a = new A();
      a.number = args.length;
      System.out.println(a.abs().toString());
    }
  }
}
