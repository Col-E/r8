// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverSingleCallerInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// This is a reproduction of b/176381203.
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

  @Test()
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableNeverSingleCallerInlineAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World 0")
        .inspect(
            inspector -> {
              ClassSubject aSubject = inspector.clazz(A.class);
              assertThat(aSubject, isPresent());
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
