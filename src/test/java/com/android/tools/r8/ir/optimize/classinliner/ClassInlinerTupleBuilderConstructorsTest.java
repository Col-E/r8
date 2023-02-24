// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.AlwaysClassInline;
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
public class ClassInlinerTupleBuilderConstructorsTest extends ClassInlinerTestBase {

  private static final String EXPECTED =
      StringUtils.lines(
          "Tuple1(false, 0, 0, 0, 0, 0, 0.0, 0.0, <null>)",
          "Tuple1(true, 77, 9977, 35, 42, 987654321123456789, -12.34, 43210.98765, s)");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInlinerTupleBuilderConstructorsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ClassInlinerTupleBuilderConstructorsTest.class)
        .addKeepMainRule(TestClass.class)
        .enableAlwaysClassInlineAnnotations()
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testJVM() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(TestClass.class);

    assertEquals(
        Collections.singleton(StringBuilder.class.getTypeName()), collectTypes(clazz.mainMethod()));

    assertThat(inspector.clazz(Tuple.class), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new Tuple().myToString());
      System.out.println(
          new Tuple(
                  true,
                  (byte) 77,
                  (short) 9977,
                  '#',
                  42,
                  987654321123456789L,
                  -12.34f,
                  43210.98765,
                  "s")
              .myToString());
    }
  }

  @AlwaysClassInline
  static class Tuple {

    boolean z;
    byte b;
    short s;
    char c;
    int i;
    long l;
    float f;
    double d;
    Object o;

    Tuple() {}

    Tuple(boolean z, byte b, short s, char c, int i, long l, float f, double d, Object o) {
      this.z = z;
      this.b = b;
      this.s = s;
      this.c = c;
      this.i = i;
      this.l = l;
      this.f = f;
      this.d = d;
      this.o = o;
    }

    String myToString() {
      return "Tuple1("
          + z
          + ", "
          + b
          + ", "
          + s
          + ", "
          + ((int) c)
          + ", "
          + i
          + ", "
          + l
          + ", "
          + f
          + ", "
          + d
          + ", "
          + (o == null ? "<null>" : o)
          + ")";
    }
  }
}
