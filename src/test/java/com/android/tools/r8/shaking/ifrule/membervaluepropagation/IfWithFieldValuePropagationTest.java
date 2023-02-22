// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IfWithFieldValuePropagationTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public IfWithFieldValuePropagationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, R.class, Layout.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-if class " + R.class.getTypeName() + " {",
            "  static int ID;",
            "}",
            "-keep class " + Layout.class.getTypeName())
        .addLibraryClasses(Library.class)
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyOutput)
        .addRunClasspathFiles(
            testForR8(parameters.getBackend())
                .addProgramClasses(Library.class)
                .addClasspathClasses(Layout.class)
                .addKeepAllClassesRule()
                .setMinApi(parameters)
                .compile()
                .writeToZip())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Layout.toString()");
  }

  private void verifyOutput(CodeInspector inspector) {
    // R.ID has been inlined.
    assertThat(inspector.clazz(R.class), not(isPresent()));

    // Layout is kept by the conditional rule.
    assertThat(inspector.clazz(Layout.class), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(Library.getViewById(R.ID));
    }
  }

  static class Library {

    public static Object getViewById(int viewId) {
      return new Layout();
    }
  }

  static class R {

    public static int ID = 42;
  }

  static class Layout {

    @Override
    public String toString() {
      return "Layout.toString()";
    }
  }
}
