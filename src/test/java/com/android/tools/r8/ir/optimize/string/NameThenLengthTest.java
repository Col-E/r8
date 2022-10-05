// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class NameThenLengthTestClass {

  private static final String NAME;
  private static final int LENGTH;
  private static final int NAME_LENGTH;

  static {
    NAME = NameThenLengthTestClass.class.getSimpleName();
    LENGTH = "NameThenLengthTestClass".length();
    NAME_LENGTH = NameThenLengthTestClass.class.getSimpleName().length();
  }

  public static void main(String... args) {
    System.out.println(NAME);
    System.out.println(LENGTH);
    System.out.println(NAME_LENGTH);

    new NameThenLengthTestClass().instanceMethod();
  }

  @NeverInline
  void instanceMethod() {
    // Note that the computed constants will be canonicalized.
    System.out.println(NameThenLengthTestClass.class.getSimpleName());
    System.out.println("NameThenLengthTestClass".length());
    System.out.println(NameThenLengthTestClass.class.getSimpleName().length());
  }
}

@RunWith(Parameterized.class)
public class NameThenLengthTest extends TestBase {
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "NameThenLengthTestClass",
      "23",
      "23",
      "NameThenLengthTestClass",
      "23",
      "23"
  );
  private static final Class<?> MAIN = NameThenLengthTestClass.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public NameThenLengthTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJVMOutput() throws Exception {
    assumeTrue("Only run JVM reference on CF runtimes", parameters.isCfRuntime());
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private static boolean isStringLength(DexMethod method) {
    return method.toSourceString().equals("int java.lang.String.length()");
  }

  private long countStringLength(MethodSubject method) {
    return method
        .streamInstructions()
        .filter(
            instructionSubject ->
                instructionSubject.isInvoke() && isStringLength(instructionSubject.getMethod()))
        .count();
  }

  private long countNonZeroConstNumber(MethodSubject method) {
    return method.streamInstructions().filter(InstructionSubject::isConstNumber).count()
        - method.streamInstructions().filter(instr -> instr.isConstNumber(0)).count();
  }

  private void test(
      SingleTestRunResult<?> result,
      int expectedStringLengthCountInClinit,
      int expectedConstNumberCountInClinit,
      int expectedStringLengthCountInInstanceMethod,
      int expectedConstNumberCountInInstanceMethod)
      throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);

    MethodSubject clinit = mainClass.clinit();
    if (result.isR8TestRunResult()) {
      assertThat(clinit, isAbsent());
    } else {
      assertThat(clinit, isPresent());
      assertEquals(expectedStringLengthCountInClinit, countStringLength(clinit));
      assertEquals(expectedConstNumberCountInClinit, countNonZeroConstNumber(clinit));
    }

    MethodSubject m = mainClass.uniqueMethodWithOriginalName("instanceMethod");
    assertThat(m, isPresent());
    assertEquals(expectedStringLengthCountInInstanceMethod, countStringLength(m));
    assertEquals(expectedConstNumberCountInInstanceMethod, countNonZeroConstNumber(m));
  }

  void configure(InternalOptions options) {
    options.testing.forceNameReflectionOptimization = true;
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime());

    D8TestRunResult result =
        testForD8()
            .debug()
            .addProgramClasses(MAIN)
            .addOptionsModification(this::configure)
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 2, 0, 2, 0);

    result =
        testForD8()
            .release()
            .addProgramClasses(MAIN)
            .addOptionsModification(this::configure)
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    // TODO(b/125303292): NAME_LENGTH is still not computed at compile time.
    test(result, 0, 0, 0, 1);
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(MAIN)
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .addDontObfuscate()
            .addOptionsModification(this::configure)
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    // No canonicalization in CF.
    int expectedConstNumber = parameters.isCfRuntime() ? 2 : 1;
    test(result, 0, 0, 0, expectedConstNumber);
  }
}
