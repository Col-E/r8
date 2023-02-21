// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.clinit;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassNotInitializedByConstClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassNotInitializedByConstClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addInnerClasses(ClassNotInitializedByConstClassTest.class)
            .addKeepMainRule(TestClass.class)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), TestClass.class);

    // Check that A.<clinit>() is removed.
    CodeInspector inspector = result.inspector();
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());
    assertThat(aClassSubject.clinit(), not(isPresent()));

    result.assertSuccessWithOutputLines(aClassSubject.getFinalName());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(A.class.getName());
    }
  }

  static class A {

    static {
      System.out.println("Hello world!");
    }
  }
}
