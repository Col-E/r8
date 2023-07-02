// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static com.android.tools.r8.ir.desugar.LambdaClass.JAVAC_EXPECTED_LAMBDA_METHOD_PREFIX;
import static com.android.tools.r8.ir.desugar.LambdaClass.R8_LAMBDA_ACCESSOR_METHOD_PREFIX;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugarInstanceLambdaWithReadsTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("false");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public DesugarInstanceLambdaWithReadsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, A.class, B.class, Consumer.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(inspector -> checkNumberOfLambdaMethods(inspector, false));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class, B.class, Consumer.class)
        .addKeepClassRules(Consumer.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(inspector -> checkNumberOfLambdaMethods(inspector, true));
  }

  private void checkNumberOfLambdaMethods(CodeInspector inspector, boolean isR8) {
    // When generating DEX, only R8 synthesizes an accessor for the javac-generated lambda$ method.
    List<FoundMethodSubject> lambdaAccessorMethods =
        inspector
            .clazz(Main.class)
            .allMethods(m -> m.getOriginalName().startsWith(R8_LAMBDA_ACCESSOR_METHOD_PREFIX));
    assertEquals(0, lambdaAccessorMethods.size());

    // When generating DEX, R8 will inline the javac-generated lambda$ method into the synthesized
    // $r8$lambda$ accessor method.
    List<FoundMethodSubject> lambdaImplementationMethods =
        inspector
            .clazz(Main.class)
            .allMethods(m -> m.getOriginalName().startsWith(JAVAC_EXPECTED_LAMBDA_METHOD_PREFIX));
    assertEquals(
        1 - BooleanUtils.intValue(parameters.isDexRuntime() && isR8),
        lambdaImplementationMethods.size());
  }

  private interface Consumer {
    void accept(String arg);
  }

  abstract static class A {
    abstract boolean contains(String item);
  }

  // A is expected to be merged into B.
  static class B extends A {
    final List<String> items;

    public B(List<String> items) {
      this.items = items;
    }

    @NeverInline
    @Override
    boolean contains(String item) {
      return items.contains(item);
    }
  }

  static class Main {
    // Field that is read from the lambda$ method (private ensures the method can't be inlined).
    private A filter;

    public Main(A filter) {
      this.filter = filter;
    }

    @NeverInline
    public static void forEach(List<String> args, Consumer fn) {
      for (String arg : args) {
        fn.accept(arg);
      }
    }

    public void foo(List<String> args) {
      forEach(args, arg -> System.out.println(filter.contains(arg)));
    }

    public static void main(String[] args) {
      new Main(new B(Arrays.asList(args))).foo(Collections.singletonList("hello!"));
    }
  }
}
