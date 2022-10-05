// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPrivate;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPublic;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PrivateKeptMembersPublicizerTest extends TestBase {

  private final TestParameters parameters;
  private final boolean withKeepAllowAccessModification;
  private final boolean withPrecondition;

  @Parameters(name = "{0}, with keep allow access modification: {1}, with precondition: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  public PrivateKeptMembersPublicizerTest(
      TestParameters parameters,
      boolean withKeepAllowAccessModification,
      boolean withPrecondition) {
    this.parameters = parameters;
    this.withKeepAllowAccessModification = withKeepAllowAccessModification;
    this.withPrecondition = withPrecondition;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(PrivateKeptMembersPublicizerTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            withPrecondition ? "-if class *" : "",
            "-keep"
                + (withKeepAllowAccessModification ? ",allowaccessmodification" : "")
                + " class "
                + typeName(TestClass.class)
                + " {",
            "  private static java.lang.String greeting;",
            "  private static void greet(java.lang.String);",
            "}")
        .allowAccessModification()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    if (withKeepAllowAccessModification) {
      assertThat(classSubject.uniqueFieldWithOriginalName("greeting"), isPublic());
      assertThat(classSubject.uniqueMethodWithOriginalName("greet"), isPublic());
    } else {
      assertThat(classSubject.uniqueFieldWithOriginalName("greeting"), isPrivate());
      assertThat(classSubject.uniqueMethodWithOriginalName("greet"), isPrivate());
    }
  }

  static class TestClass {

    private static String greeting = "Hello world!";

    public static void main(String[] args) {
      greet(greeting);
    }

    private static void greet(String message) {
      System.out.println(message);
    }
  }
}
