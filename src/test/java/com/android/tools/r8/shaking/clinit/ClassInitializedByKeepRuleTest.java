// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.clinit;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

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
public class ClassInitializedByKeepRuleTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInitializedByKeepRuleTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ClassInitializedByKeepRuleTest.class)
        .addKeepClassRules(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    // Check that A.<clinit>() is not removed.
    ClassSubject aClassSubject = inspector.clazz(TestClass.class);
    assertThat(aClassSubject, isPresent());
    assertThat(aClassSubject.clinit(), isPresent());
  }

  static class TestClass {

    static {
      System.out.print("Hello world");
    }
  }
}
