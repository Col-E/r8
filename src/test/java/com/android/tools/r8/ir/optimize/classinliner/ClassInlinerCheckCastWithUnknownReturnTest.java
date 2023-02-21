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
public class ClassInlinerCheckCastWithUnknownReturnTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInlinerCheckCastWithUnknownReturnTest(TestParameters parameters) {
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
        .assertFailureWithErrorThatThrows(ClassCastException.class)
        .inspectFailure(
            inspector -> {
              ClassSubject aSubject = inspector.clazz(A.class);
              assertThat(aSubject, isPresent());
            });
  }

  public static class A {

    public int number;

    @NeverSingleCallerInline
    public Object abs() {
      if (number == 0) {
        return new Object();
      }
      return this;
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A a = new A();
      a.number = args.length;
      A returnedA = (A) (a.abs());
      System.out.println("Hello World");
    }
  }
}
