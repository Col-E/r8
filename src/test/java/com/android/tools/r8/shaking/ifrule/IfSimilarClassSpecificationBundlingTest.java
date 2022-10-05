// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.InternalOptions.TestingOptions.ProguardIfRuleEvaluationData;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This test specifically tests how many times we evaluate if rules in the enqueuer. Having multiple
 * generated appt2 rules that has same class part and have constant sequential rules can be
 * optimized considerably. The fixed numbers are what was current at the time. Feel free to update
 * these when changing the if-rule evaluator.
 */
@RunWith(Parameterized.class)
public class IfSimilarClassSpecificationBundlingTest extends TestBase {

  public static final String EXPECTED_LINE_OUTPUT = 0xCAFEBABE + "";

  public static class A {
    void a() {}
  }

  public static class B {
    void b() {}
  }

  public static class C {
    void c() {}
  }

  public static class RA {
    static int keepA = 0xCAFEBABE;
  }

  public static class RB {
    static int keepB = 0xCAFEBABE;
  }

  public static class RC {
    static int keepC = 0xCAFEBABE;
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(RA.keepA);
      System.out.println(RB.keepB);
      System.out.println(RC.keepC);
    }
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public IfSimilarClassSpecificationBundlingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testBundlingOfIfRulesWithConstantSequent()
      throws IOException, CompilationFailedException, ExecutionException {
    runTest(
        8,
        18,
        "-if class **$R* { int keepA; }",
        "-keep class " + A.class.getTypeName() + " { void a(); }",
        "-if class **$R* { int keepB; }",
        "-keep class " + B.class.getTypeName() + " { void b(); }",
        "-if class **$R* { int keepC; }",
        "-keep class " + C.class.getTypeName() + " { void c(); }");
  }

  @Test
  public void testBundlingOfIfRulesWithNonConstantSequent()
      throws IOException, CompilationFailedException, ExecutionException {
    runTest(
        22,
        36,
        "-if class **$R* { int keepA; }",
        "-keep class"
            + " com.android.tools.r8.shaking.ifrule.IfSimilarClassSpecificationBundlingTest$<2> {"
            + " void a(); }",
        "-if class **$R* { int keepB; }",
        "-keep class"
            + " com.android.tools.r8.shaking.ifrule.IfSimilarClassSpecificationBundlingTest$<2> {"
            + " void b(); }",
        "-if class **$R* { int keepC; }",
        "-keep class"
            + " com.android.tools.r8.shaking.ifrule.IfSimilarClassSpecificationBundlingTest$<2> {"
            + " void c(); }");
  }

  @Test
  public void testBundlingOfIfRulesWithPositiveList()
      throws IOException, CompilationFailedException, ExecutionException {
    runTest(
        8,
        18,
        "-if class **$R*,**$X { int keepA; }",
        "-keep class " + A.class.getTypeName() + " { void a(); }",
        "-if class **$R*,**$X { int keepB; }",
        "-keep class " + B.class.getTypeName() + " { void b(); }",
        "-if class **$R*,**$X { int keepC; }",
        "-keep class " + C.class.getTypeName() + " { void c(); }");
  }

  private void runTest(
      int expectedClassEvaluations, int expectedMemberEvaluations, String... keepRules)
      throws IOException, CompilationFailedException, ExecutionException {
    class Box {
      private ProguardIfRuleEvaluationData data;
    }
    Box box = new Box();
    testForR8(parameters.getBackend())
        .addInnerClasses(IfSimilarClassSpecificationBundlingTest.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(keepRules)
        .setMinApi(parameters.getRuntime())
        .addOptionsModification(
            options -> {
              options.testing.measureProguardIfRuleEvaluations = true;
              box.data = options.testing.proguardIfRuleEvaluationData;
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            EXPECTED_LINE_OUTPUT, EXPECTED_LINE_OUTPUT, EXPECTED_LINE_OUTPUT)
        .inspect(
            codeInspector -> {
              ClassSubject aSubject = codeInspector.clazz(A.class);
              assertThat(aSubject, isPresent());
              assertThat(aSubject.uniqueMethodWithOriginalName("a"), isPresent());
              ClassSubject bSubject = codeInspector.clazz(B.class);
              assertThat(bSubject, isPresent());
              assertThat(bSubject.uniqueMethodWithOriginalName("b"), isPresent());
              ClassSubject cSubject = codeInspector.clazz(C.class);
              assertThat(cSubject, isPresent());
              assertThat(cSubject.uniqueMethodWithOriginalName("c"), isPresent());
            });
    assertEquals(expectedClassEvaluations, box.data.numberOfProguardIfRuleClassEvaluations);
    assertEquals(expectedMemberEvaluations, box.data.numberOfProguardIfRuleMemberEvaluations);
  }
}
