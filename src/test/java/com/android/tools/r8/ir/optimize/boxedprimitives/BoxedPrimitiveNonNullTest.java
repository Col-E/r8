// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.boxedprimitives;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BoxedPrimitiveNonNullTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public BoxedPrimitiveNonNullTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNonNull() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .enableInliningAnnotations()
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyNoBranch)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("true", "1", "1", "c", "1", "1", "1.0", "1.0");
  }

  private void verifyNoBranch(CodeInspector codeInspector) {
    for (FoundMethodSubject nonMain :
        codeInspector.clazz(TestClass.class).allMethods(m -> !m.getOriginalName().equals("main"))) {
      assertTrue(nonMain.streamInstructions().noneMatch(InstructionSubject::isIf));
    }
  }

  public static class TestClass {

    public static void main(String[] args) {
      booleanTest();
      byteTest();
      shortTest();
      characterTest();
      integerTest();
      longTest();
      floatTest();
      doubleTest();
    }

    @NeverInline
    private static void booleanTest() {
      Boolean boxed = Boolean.valueOf(true);
      if (boxed == null) {
        System.out.println("null int");
      } else {
        System.out.println(boxed.booleanValue());
      }
    }

    @NeverInline
    private static void byteTest() {
      Byte boxed = Byte.valueOf((byte) 1);
      if (boxed == null) {
        System.out.println("null int");
      } else {
        System.out.println(boxed.byteValue());
      }
    }

    @NeverInline
    private static void shortTest() {
      Short boxed = Short.valueOf((short) 1);
      if (boxed == null) {
        System.out.println("null int");
      } else {
        System.out.println(boxed.shortValue());
      }
    }

    @NeverInline
    private static void characterTest() {
      Character boxed = Character.valueOf('c');
      if (boxed == null) {
        System.out.println("null int");
      } else {
        System.out.println(boxed.charValue());
      }
    }

    @NeverInline
    private static void integerTest() {
      Integer boxed = Integer.valueOf(1);
      if (boxed == null) {
        System.out.println("null int");
      } else {
        System.out.println(boxed.byteValue());
      }
    }

    @NeverInline
    private static void longTest() {
      Long boxed = Long.valueOf(1L);
      if (boxed == null) {
        System.out.println("null int");
      } else {
        System.out.println(boxed.longValue());
      }
    }

    @NeverInline
    private static void floatTest() {
      Float boxed = Float.valueOf(1.0f);
      if (boxed == null) {
        System.out.println("null int");
      } else {
        System.out.println(boxed.floatValue());
      }
    }

    @NeverInline
    private static void doubleTest() {
      Double boxed = Double.valueOf(1.0d);
      if (boxed == null) {
        System.out.println("null int");
      } else {
        System.out.println(boxed.doubleValue());
      }
    }
  }
}
