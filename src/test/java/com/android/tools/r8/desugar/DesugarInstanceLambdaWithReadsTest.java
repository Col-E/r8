// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
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
        .inspect(this::checkJustOneLambdaImplementationMethod);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class, B.class, Consumer.class)
        .addKeepClassRules(Consumer.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkJustOneLambdaImplementationMethod);
  }

  private void checkJustOneLambdaImplementationMethod(CodeInspector inspector) {
    List<FoundMethodSubject> lambdaImplementationMethods =
        inspector.clazz(Main.class).allMethods(m -> m.getOriginalName().startsWith("lambda$"));
    assertEquals(1, lambdaImplementationMethods.size());
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
