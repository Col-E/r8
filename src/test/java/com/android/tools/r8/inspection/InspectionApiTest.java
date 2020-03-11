// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.inspection;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.inspector.Inspector;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InspectionApiTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InspectionApiTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addProgramClasses(TestClass.class)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED);
      return;
    }
    testForD8()
        .addProgramClasses(TestClass.class)
        .setMinApi(parameters.getApiLevel())
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
        .setMinApi(parameters.getApiLevel())
        .apply(b -> b.getBuilder().addOutputInspection(this::inspection))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
    assertFound();
  }

  ClassReference foundClass = null;
  FieldReference foundField = null;
  MethodReference foundMethod = null;

  private void inspection(Inspector inspector) {
    inspector.forEachClass(
        classInspector -> {
          foundClass = classInspector.getClassReference();
          classInspector.forEachField(
              fieldInspector -> {
                foundField = fieldInspector.getFieldReference();
              });
          classInspector.forEachMethod(
              methodInspector -> {
                // Ignore clinit (which is removed in R8).
                if (!methodInspector.getMethodReference().getMethodName().equals("<clinit>")) {
                  foundMethod = methodInspector.getMethodReference();
                }
              });
        });
  }

  private void assertFound() throws Exception {
    assertEquals(Reference.classFromClass(TestClass.class), foundClass);
    assertEquals(Reference.fieldFromField(TestClass.class.getDeclaredField("foo")), foundField);
    assertEquals(
        Reference.methodFromMethod(TestClass.class.getDeclaredMethod("main", String[].class)),
        foundMethod);
  }

  static class TestClass {
    public static int foo = 42;

    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }
}
