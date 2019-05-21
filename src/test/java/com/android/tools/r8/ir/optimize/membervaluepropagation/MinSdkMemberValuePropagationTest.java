// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MinSdkMemberValuePropagationTest extends TestBase {

  private final TestParameters parameters;
  private final String rule;
  private final String value;

  @Parameterized.Parameters(name = "{0}, rule: {1}, value: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().build(),
        ImmutableList.of("assumenosideeffects", "assumevalues"),
        ImmutableList.of("42", "42..43"));
  }

  public MinSdkMemberValuePropagationTest(TestParameters parameters, String rule, String value) {
    this.parameters = parameters;
    this.rule = rule;
    this.value = value;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-" + rule + " class " + Library.class.getTypeName() + " {",
            "  static int MIN_SDK return " + value + ";",
            "}")
        .addLibraryClasses(Library.class)
        .addLibraryFiles(runtimeJar(parameters))
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(this::verifyOutput)
        .addRunClasspathFiles(
            testForR8(parameters.getBackend())
                .addProgramClasses(Library.class)
                .addKeepAllClassesRule()
                .setMinApi(parameters.getRuntime())
                .compile()
                .writeToZip())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void verifyOutput(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());

    MethodSubject mainSubject = classSubject.mainMethod();
    assertThat(mainSubject, isPresent());

    boolean readsMinSdkField =
        mainSubject
            .streamInstructions()
            .anyMatch(x -> x.isStaticGet() && x.getField().name.toString().equals("MIN_SDK"));
    assertEquals(rule.equals("assumevalues"), readsMinSdkField);
  }

  static class TestClass {

    public static void main(String[] args) {
      if (Library.MIN_SDK >= 42) {
        System.out.println("Hello world!");
      }
    }
  }

  static class Library {

    static int MIN_SDK = -1;
  }
}
