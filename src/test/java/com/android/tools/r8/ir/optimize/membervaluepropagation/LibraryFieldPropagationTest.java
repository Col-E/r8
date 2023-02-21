// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LibraryFieldPropagationTest extends TestBase {

  private static final String MAIN = "TestClass";
  private static List<byte[]> programClassFileData;

  private final TestParameters parameters;
  private final boolean withAssumeValuesRule;

  @Parameterized.Parameters(name = "{0}, with assume values rule: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public LibraryFieldPropagationTest(TestParameters parameters, boolean withAssumeValuesRule) {
    this.parameters = parameters;
    this.withAssumeValuesRule = withAssumeValuesRule;
  }

  @BeforeClass
  public static void generateProgramInput() throws Exception {
    // Generate a program that prints Thread.MIN_PRIORITY, which is a final field that has the
    // constant value 1. The program is being generated via Jasmin, because javac will inline the
    // field.
    JasminBuilder jasminBuilder = new JasminBuilder();
    jasminBuilder
        .addClass(MAIN)
        .staticMethodBuilder("main", ImmutableList.of("[Ljava/lang/String;"), "V")
        .setCode(
            "getstatic java/lang/System/out Ljava/io/PrintStream;",
            "getstatic java/lang/Thread/MIN_PRIORITY I",
            "invokevirtual java/io/PrintStream/println(I)V",
            "return")
        .build();
    programClassFileData = jasminBuilder.buildClasses();
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("1");
    testForR8(parameters.getBackend())
        .addProgramClassFileData(programClassFileData)
        .addKeepMainRule(MAIN)
        .addKeepRules(
            withAssumeValuesRule
                ? "-assumevalues class java.lang.Thread { public int MIN_PRIORITY return 1; }"
                : "")
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyFieldValueNotPropagated)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(expectedOutput);
  }

  private void verifyFieldValueNotPropagated(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(MAIN);
    assertThat(classSubject, isPresent());

    MethodSubject methodSubject = classSubject.mainMethod();
    assertThat(methodSubject, isPresent());

    if (withAssumeValuesRule) {
      // A const-number instruction has been introduced.
      assertTrue(methodSubject.streamInstructions().anyMatch(InstructionSubject::isConstNumber));

      // The static-get instruction is still there, since it could in principle lead to class
      // initialization.
      assertTrue(
          methodSubject
              .streamInstructions()
              .anyMatch(
                  instruction ->
                      instruction.isStaticGet()
                          && instruction.getField().name.toSourceString().equals("MIN_PRIORITY")));
    } else {
      // Verify that the static-get instruction is still there, and that no const-number instruction
      // has been introduced.
      assertTrue(
          methodSubject
              .streamInstructions()
              .anyMatch(
                  instruction ->
                      instruction.isStaticGet()
                          && instruction.getField().name.toSourceString().equals("MIN_PRIORITY")));
      assertTrue(methodSubject.streamInstructions().noneMatch(InstructionSubject::isConstNumber));
    }
  }
}
