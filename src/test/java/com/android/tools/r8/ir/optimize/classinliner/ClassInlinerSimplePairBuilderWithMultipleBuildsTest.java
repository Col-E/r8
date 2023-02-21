// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ClassInlinerSimplePairBuilderWithMultipleBuildsTest extends ClassInlinerTestBase {

  private static final String EXPECTED =
      StringUtils.lines(
          "Pair(<null>, <null>)",
          "[before] first = null",
          "[after] first = f1",
          "Pair(f1, <null>)",
          "[before] second = null",
          "[after] second = s2",
          "Pair(f1, s2)");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInlinerSimplePairBuilderWithMultipleBuildsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ClassInlinerSimplePairBuilderWithMultipleBuildsTest.class)
        .addKeepMainRule(TestClass.class)
        .enableAlwaysInliningAnnotations()
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testExpectedBehavior() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(ClassInlinerSimplePairBuilderWithMultipleBuildsTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(TestClass.class);

    // Note that Pair created instances were also inlined in the following method since
    // we use 'System.out.println(pX.toString())', if we used 'System.out.println(pX)'
    // as in the above method, the instance of pair would be passed to println() which
    // would make it not eligible for inlining.
    assertEquals(
        Collections.singleton(StringBuilder.class.getTypeName()), collectTypes(clazz.mainMethod()));

    assertThat(inspector.clazz(PairBuilder.class), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      PairBuilder<String, String> builder = new PairBuilder<>();
      Pair p1 = builder.build();
      System.out.println(p1.myToString());
      builder.setFirst("f1");
      Pair p2 = builder.build();
      System.out.println(p2.myToString());
      builder.setSecond("s2");
      Pair p3 = builder.build();
      System.out.println(p3.myToString());
    }
  }

  static class Pair<F, S> {

    final F first;
    final S second;

    Pair(F first, S second) {
      this.first = first;
      this.second = second;
    }

    String myToString() {
      return "Pair("
          + (first == null ? "<null>" : first)
          + ", "
          + (second == null ? "<null>" : second)
          + ")";
    }
  }

  static class PairBuilder<F, S> {

    F first;
    S second = null;

    void setFirst(F first) {
      System.out.println("[before] first = " + this.first);
      this.first = first;
      System.out.println("[after] first = " + this.first);
    }

    void setSecond(S second) {
      System.out.println("[before] second = " + this.second);
      this.second = second;
      System.out.println("[after] second = " + this.second);
    }

    @AlwaysInline
    public Pair<F, S> build() {
      return new Pair<>(first, second);
    }
  }
}
