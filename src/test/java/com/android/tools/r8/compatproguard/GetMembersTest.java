// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compatproguard;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexAputObject;
import com.android.tools.r8.dex.code.DexConst4;
import com.android.tools.r8.dex.code.DexConstClass;
import com.android.tools.r8.dex.code.DexConstString;
import com.android.tools.r8.dex.code.DexFilledNewArray;
import com.android.tools.r8.dex.code.DexInvokeVirtual;
import com.android.tools.r8.dex.code.DexMoveResultObject;
import com.android.tools.r8.dex.code.DexNewArray;
import com.android.tools.r8.dex.code.DexReturnVoid;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GetMembersTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addDontOptimize()
        .addDontShrink()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(Main.class);
    assertThat(classSubject, isPresent());
    inspectGetFieldTest(classSubject.uniqueMethodWithOriginalName("getFieldTest"));
    inspectGetMethodTest(classSubject.uniqueMethodWithOriginalName("getMethodTest"));
  }

  private void inspectGetFieldTest(MethodSubject method) {
    DexCode code = method.getMethod().getCode().asDexCode();
    assertTrue(code.instructions[0] instanceof DexConstClass);
    assertTrue(code.instructions[1] instanceof DexConstString);
    DexConstString constString = (DexConstString) code.instructions[1];
    assertNotEquals("foo", constString.getString().toString());
    assertTrue(code.instructions[2] instanceof DexInvokeVirtual);
    assertTrue(code.instructions[3] instanceof DexReturnVoid);
  }

  private void inspectGetMethodTest(MethodSubject method) {
    // Accept either array construction style (differs based on minSdkVersion).
    DexCode code = method.getMethod().getCode().asDexCode();
    if (code.instructions[1] instanceof DexFilledNewArray) {
      assertTrue(code.instructions[0] instanceof DexConstClass);
      assertTrue(code.instructions[1] instanceof DexFilledNewArray);
      assertTrue(code.instructions[2] instanceof DexMoveResultObject);
      assertTrue(code.instructions[3] instanceof DexConstClass);
      assertTrue(code.instructions[4] instanceof DexConstString);
      assertNotEquals("foo", code.instructions[4].asConstString().getString().toString());
      assertTrue(code.instructions[5] instanceof DexInvokeVirtual);
      assertTrue(code.instructions[6] instanceof DexReturnVoid);
    } else {
      assertTrue(code.instructions[0] instanceof DexConstClass);
      assertTrue(code.instructions[1] instanceof DexConst4);
      assertTrue(code.instructions[2] instanceof DexNewArray);
      assertTrue(code.instructions[3] instanceof DexConst4);
      assertTrue(code.instructions[4] instanceof DexAputObject);
      assertTrue(code.instructions[5] instanceof DexConstClass);
      assertTrue(code.instructions[6] instanceof DexConstString);
      assertNotEquals("foo", code.instructions[6].asConstString().getString().toString());
      assertTrue(code.instructions[7] instanceof DexInvokeVirtual);
      assertTrue(code.instructions[8] instanceof DexReturnVoid);
    }
  }

  static class Main {

    public static void main(String[] args) throws NoSuchFieldException, NoSuchMethodException {
      getFieldTest();
      getMethodTest();
    }

    static void getFieldTest() throws NoSuchFieldException {
      Boo.class.getField("foo");
    }

    static void getMethodTest() throws NoSuchMethodException {
      Boo.class.getMethod("foo", String.class);
    }
  }

  static class Boo {

    public static String foo;

    public static void foo(String s) {}
  }
}
