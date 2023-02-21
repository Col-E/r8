// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

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
public class ClassInlinerBuilderWithControlFlowTest extends ClassInlinerTestBase {

  private static final String EXPECTED =
      StringUtils.lines(
          "flow = >0>0>-1>1234>1236>7>3>1240>1266>6>1248>3798>8>1254>10>1264>8885>12>1273>19063>14>"
              + "16>1288>39449>18>1301>80229>20>1315>161806>23>1335>324994>25>1353>651383>27>1372>1"
              + "304183>29>1392>2609806>32>1418>5221092>34>1442>10443683>36>1467>20888893>38>1493>4"
              + "1779342>40>1520>42>1551>83560313>44>1581>167122280>46>1612>334246248>48>1644>66849"
              + "4219>50>1677>1336990197>52>54>1716>-1620985089>56>1753>1052998963>58>1791>21059998"
              + "12>60>1830>-82965744>62>1870>-165929517>64>1911>-331857019>");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInlinerBuilderWithControlFlowTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ClassInlinerBuilderWithControlFlowTest.class)
        .addKeepMainRule(TestClass.class)
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
        .addInnerClasses(ClassInlinerBuilderWithControlFlowTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(TestClass.class);

    assertEquals(
        Collections.singleton(StringBuilder.class.getTypeName()), collectTypes(clazz.mainMethod()));

    assertThat(inspector.clazz(ControlFlow.class), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      ControlFlow flow = new ControlFlow(-1, 2, 7);
      for (int k = 0; k < 25; k++) {
        if (k % 3 == 0) {
          flow.foo(k);
        } else if (k % 3 == 1) {
          flow.bar(1, 2, 3, 4);
        }
      }
      System.out.println("flow = " + flow.toString());
    }
  }

  static class ControlFlow {

    int a;
    int b;
    int c = 1234;
    int d;
    String s = ">";

    ControlFlow(int b, int c, int d) {
      this.s += this.a++ + ">";
      this.s += this.b + ">";
      this.b = b;
      this.s += this.b + ">";
      this.s += this.c + ">";
      this.c += c;
      this.s += this.c + ">";
      this.s += (this.d = d) + ">";
    }

    void foo(int count) {
      for (int i = 0; i < count; i++) {
        switch (i % 4) {
          case 0:
            this.s += ++this.a + ">";
            break;
          case 1:
            this.c += this.b;
            this.s += this.c + ">";
            break;
          case 2:
            this.d += this.d++ + this.c++ + this.b++ + this.a++;
            this.s += this.d + ">";
            break;
        }
      }
    }

    void bar(int a, int b, int c, int d) {
      this.a += a;
      this.b += b;
      this.c += c;
      this.d += d;
    }

    @Override
    public String toString() {
      return s;
    }
  }
}
