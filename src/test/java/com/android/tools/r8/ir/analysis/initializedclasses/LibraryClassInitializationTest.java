// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.initializedclasses;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.Serializable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LibraryClassInitializationTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public LibraryClassInitializationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(LibraryClassInitializationTest.class)
            .addKeepMainRule(TestClass.class)
            .setMinApi(parameters.getRuntime())
            .compile()
            .inspector();
    assertThat(inspector.clazz(ClassWithoutClassInitialization.class), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      // The instantiation can only be removed if we assume that the class initialization of
      // Serializable may not have side effects.
      new ClassWithoutClassInitialization();
    }
  }

  static class ClassWithoutClassInitialization implements Serializable {}
}
