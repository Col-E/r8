// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.membervaluepropagation.StatePropagationTest.TestClass.Data;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StatePropagationTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StatePropagationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(StatePropagationTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("1", "1.0", "2", "2.0");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject data = inspector.clazz(Data.class);
    assertEquals(0, data.allInstanceFields().size());
  }

  static class TestClass {

    @NeverClassInline
    static class Data {
      final int i;
      final float j;

      private Data(int i, float j) {
        this.i = i;
        this.j = j;
      }
    }

    @NeverInline
    public static Data getData1() {
      return new Data(1, 1.0f);
    }

    @NeverInline
    public static Data getData2() {
      return new Data(2, 2.0f);
    }

    public static void main(String[] args) {
      Data data1 = getData1();
      Data data2 = getData2();
      System.out.println(data1.i);
      System.out.println(data1.j);
      System.out.println(data2.i);
      System.out.println(data2.j);
    }
  }
}
