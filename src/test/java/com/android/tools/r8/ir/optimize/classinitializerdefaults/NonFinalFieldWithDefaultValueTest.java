// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinitializerdefaults;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NonFinalFieldWithDefaultValueTest extends TestBase {

  private final String EXPECTED_OUTPUT =
      StringUtils.lines("Hello world (f1=1, f3=0)!", "1", "2", "3", "-1", "-2", "-3");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public NonFinalFieldWithDefaultValueTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForD8()
        .addInnerClasses(NonFinalFieldWithDefaultValueTest.class)
        .setMinApi(parameters.getApiLevel())
        // Class initializer defaults optimization only runs in release mode.
        .release()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(NonFinalFieldWithDefaultValueTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    // No instructions can read field f1 before it is assigned, and therefore, we can safely
    // remove the static-put instruction and update the static value of the field.
    FieldSubject f1FieldSubject = testClassSubject.uniqueFieldWithOriginalName("f1");
    assertThat(f1FieldSubject, isPresent());
    assertNotNull(f1FieldSubject.getField().getStaticValue());
    assertTrue(f1FieldSubject.getField().getStaticValue().isDexValueInt());
    assertEquals(1, f1FieldSubject.getField().getStaticValue().asDexValueInt().getValue());

    // Field f3 is assigned after an instruction that could read it, and therefore, we cannot safely
    // remove the static-put instruction and update the static value of the field.
    FieldSubject f3FieldSubject = testClassSubject.uniqueFieldWithOriginalName("f3");
    assertThat(f3FieldSubject, isPresent());
    assertNotNull(f3FieldSubject.getField().getStaticValue());
    assertTrue(f3FieldSubject.getField().getStaticValue().isDexValueInt());
    assertEquals(0, f3FieldSubject.getField().getStaticValue().asDexValueInt().getValue());
  }

  static class TestClass {

    static int f1 = 1;
    static Greeter f2 = new Greeter(2);
    static int f3 = 3;

    public static void main(String[] args) {
      System.out.println(f1);
      System.out.println(f2.value);
      System.out.println(f3);
      updateFields();
      System.out.println(f1);
      System.out.println(f2.value);
      System.out.println(f3);
    }

    @NeverInline
    static void updateFields() {
      f1 = -1;
      f2.value = -2;
      f3 = -3;
    }
  }

  static class Greeter {

    int value;

    Greeter(int value) {
      System.out.println("Hello world (f1=" + TestClass.f1 + ", f3=" + TestClass.f3 + ")!");
      this.value = value;
    }
  }
}
