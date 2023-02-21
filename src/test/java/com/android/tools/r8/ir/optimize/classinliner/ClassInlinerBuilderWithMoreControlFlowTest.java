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
public class ClassInlinerBuilderWithMoreControlFlowTest extends ClassInlinerTestBase {

  private static final String EXPECTED = StringUtils.lines("Pos(x=0, y=10)");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInlinerBuilderWithMoreControlFlowTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ClassInlinerBuilderWithMoreControlFlowTest.class)
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
        .addInnerClasses(ClassInlinerBuilderWithMoreControlFlowTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(TestClass.class);

    assertEquals(
        Collections.singleton(StringBuilder.class.getTypeName()), collectTypes(clazz.mainMethod()));

    assertThat(inspector.clazz(Pos.class), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      String str = "1234567890";
      Pos pos = new Pos();
      while (pos.y < str.length()) {
        pos.x = pos.y;
        pos.y = pos.x;

        if (str.charAt(pos.x) != '*') {
          if ('0' <= str.charAt(pos.y) && str.charAt(pos.y) <= '9') {
            while (pos.y < str.length() && '0' <= str.charAt(pos.y) && str.charAt(pos.y) <= '9') {
              pos.y++;
            }
          }
        }
      }
      System.out.println(pos.myToString());
    }
  }

  static class Pos {

    int x = 0;
    int y = 0;

    String myToString() {
      return "Pos(x=" + x + ", y=" + y + ")";
    }
  }
}
