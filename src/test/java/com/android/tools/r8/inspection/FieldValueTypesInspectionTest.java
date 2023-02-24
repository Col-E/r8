// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.inspection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.inspector.Inspector;
import com.android.tools.r8.inspector.ValueInspector;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FieldValueTypesInspectionTest extends TestBase {

  static final String EXPECTED =
      StringUtils.lines(
          "" + TestClass.z,
          "" + TestClass.b,
          "" + TestClass.c,
          "" + TestClass.s,
          "" + TestClass.i,
          "" + TestClass.j,
          "" + TestClass.f,
          "" + TestClass.d,
          "foo");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public FieldValueTypesInspectionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addProgramClasses(TestClass.class)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED);
      return;
    }
    testForD8()
        .addProgramClasses(TestClass.class)
        .setMinApi(parameters)
        .apply(b -> b.getBuilder().addOutputInspection(this::inspection))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
    assertFound();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepClassAndMembersRules(TestClass.class)
        .setMinApi(parameters)
        .apply(b -> b.getBuilder().addOutputInspection(this::inspection))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
    assertFound();
  }

  private int foundFields = 0;

  private void inspection(Inspector inspector) {
    inspector.forEachClass(
        classInspector -> {
          classInspector.forEachField(
              fieldInspector -> {
                foundFields++;
                FieldReference reference = fieldInspector.getFieldReference();
                ValueInspector value = fieldInspector.getInitialValue().get();
                assertEquals(reference.getFieldType(), value.getTypeReference());
                String name = reference.getFieldName();
                boolean isBoolean = name.equals("z");
                boolean isByte = name.equals("b");
                boolean isChar = name.equals("c");
                boolean isShort = name.equals("s");
                boolean isInt = name.equals("i");
                boolean isLong = name.equals("j");
                boolean isFloat = name.equals("f");
                boolean isDouble = name.equals("d");
                boolean isString = name.equals("str");
                assertEquals(isBoolean, value.isBooleanValue());
                assertEquals(isByte, value.isByteValue());
                assertEquals(isChar, value.isCharValue());
                assertEquals(isShort, value.isShortValue());
                assertEquals(isInt, value.isIntValue());
                assertEquals(isLong, value.isLongValue());
                assertEquals(isFloat, value.isFloatValue());
                assertEquals(isDouble, value.isDoubleValue());
                assertEquals(isString, value.isStringValue());
                if (isBoolean) {
                  assertEquals(Reference.BOOL, reference.getFieldType());
                  assertEquals(TestClass.z, value.asBooleanValue().getBooleanValue());
                } else {
                  assertNull(value.asBooleanValue());
                }
                if (isByte) {
                  assertEquals(Reference.BYTE, reference.getFieldType());
                  assertEquals(TestClass.b, value.asByteValue().getByteValue());
                } else {
                  assertNull(value.asByteValue());
                }
                if (isChar) {
                  assertEquals(Reference.CHAR, reference.getFieldType());
                  assertEquals(TestClass.c, value.asCharValue().getCharValue());
                } else {
                  assertNull(value.asCharValue());
                }
                if (isShort) {
                  assertEquals(Reference.SHORT, reference.getFieldType());
                  assertEquals(TestClass.s, value.asShortValue().getShortValue());
                } else {
                  assertNull(value.asShortValue());
                }
                if (isInt) {
                  assertEquals(Reference.INT, reference.getFieldType());
                  assertEquals(TestClass.i, value.asIntValue().getIntValue());
                } else {
                  assertNull(value.asIntValue());
                }
                if (isLong) {
                  assertEquals(Reference.LONG, reference.getFieldType());
                  assertEquals(TestClass.j, value.asLongValue().getLongValue());
                } else {
                  assertNull(value.asLongValue());
                }
                if (isFloat) {
                  assertEquals(Reference.FLOAT, reference.getFieldType());
                  assertEquals(
                      Float.floatToRawIntBits(TestClass.f),
                      Float.floatToRawIntBits(value.asFloatValue().getFloatValue()));
                } else {
                  assertNull(value.asFloatValue());
                }
                if (isDouble) {
                  assertEquals(Reference.DOUBLE, reference.getFieldType());
                  assertEquals(
                      Double.doubleToRawLongBits(TestClass.d),
                      Double.doubleToRawLongBits(value.asDoubleValue().getDoubleValue()));
                } else {
                  assertNull(value.asDoubleValue());
                }
                if (isString) {
                  assertEquals(Reference.classFromClass(String.class), reference.getFieldType());
                  assertEquals(TestClass.str, value.asStringValue().getStringValue());
                } else {
                  assertNull(value.asStringValue());
                }
              });
        });
  }

  private void assertFound() {
    assertEquals(9, foundFields);
  }

  static class TestClass {
    public static final boolean z = true;
    public static final byte b = 2;
    public static final char c = 4;
    public static final short s = 8;
    public static final int i = 16;
    public static final long j = 32L;
    public static final float f = 64.1F;
    public static final double d = 128.1D;
    public static final String str = "foo";

    public static void main(String[] args) {
      System.out.println(z);
      System.out.println(b);
      System.out.println(c);
      System.out.println(s);
      System.out.println(i);
      System.out.println(j);
      System.out.println(f);
      System.out.println(d);
      System.out.println(str);
    }
  }
}
