// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.inspection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.inspector.Inspector;
import com.android.tools.r8.inspector.ValueInspector;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.StringUtils;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FieldFlagsAndValueInspectionTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("30");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public FieldFlagsAndValueInspectionTest(TestParameters parameters) {
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
        .apply(b -> b.getBuilder().addOutputInspection(inspector -> inspection(inspector, false)))
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
        .apply(b -> b.getBuilder().addOutputInspection(inspector -> inspection(inspector, true)))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
    assertFound();
  }

  private int foundFields = 0;

  private void inspection(Inspector inspector, boolean isR8) {
    inspector.forEachClass(
        classInspector -> {
          classInspector.forEachField(
              fieldInspector -> {
                foundFields++;
                String name = fieldInspector.getFieldReference().getFieldName();
                assertEquals(name.contains("s"), fieldInspector.isStatic());
                assertEquals(name.contains("f"), fieldInspector.isFinal());
                assertEquals(Reference.INT, fieldInspector.getFieldReference().getFieldType());
                Optional<ValueInspector> value = fieldInspector.getInitialValue();
                if (fieldInspector.isStatic() && fieldInspector.isFinal()) {
                  // The static final 'sfi' is static initialized to 2.
                  assertTrue(value.isPresent());
                  assertEquals(2, value.get().asIntValue().getIntValue());
                } else if (fieldInspector.isStatic()) {
                  // The static 'si' is default initialized to 0 and clinit sets it to 4.
                  // R8 optimizes that to directly set 4.
                  assertTrue(value.isPresent());
                  assertEquals(isR8 ? 4 : 0, value.get().asIntValue().getIntValue());
                } else {
                  assertFalse(value.isPresent());
                }
              });
        });
  }

  private void assertFound() {
    assertEquals(4, foundFields);
  }

  static class TestClass {
    public static final int sfi = 2;
    public static int si = 4;
    public final int fi = 8;
    public int i = 16;

    public static void main(String[] args) {
      TestClass obj = new TestClass();
      System.out.println(obj.sfi + obj.si + obj.fi + obj.i);
    }
  }
}
