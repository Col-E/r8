// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.InlinableStaticFinalFieldPreconditionDiagnostic;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IfOnStaticFinalFieldTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public IfOnStaticFinalFieldTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("A", "B");
  }

  @Test
  public void testRuleMatchingOnlyStaticFinalFieldsYieldWarning() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-if class " + StaticFinalField.class.getTypeName() + " { int f; }",
            "-keep class " + A.class.getTypeName())
        .addKeepRules(
            "-if class " + StaticNonFinalField.class.getTypeName() + " { int f; }",
            "-keep class " + B.class.getTypeName())
        .setMinApi(parameters)
        .allowDiagnosticMessages()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertOnlyWarnings()
                    .assertWarningsMatch(
                        allOf(
                            diagnosticType(InlinableStaticFinalFieldPreconditionDiagnostic.class),
                            diagnosticMessage(containsString("StaticFinalField.f")))))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("ClassNotFoundException: A", "B");
  }

  @Test
  public void testRuleMatchingMoreHasWarning() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-if class * { int f; } -keep class " + B.class.getTypeName())
        .setMinApi(parameters)
        .allowDiagnosticMessages()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertWarningsMatch(
                        allOf(
                            diagnosticType(InlinableStaticFinalFieldPreconditionDiagnostic.class),
                            diagnosticMessage(containsString("StaticFinalField.f"))))
                    .assertOnlyWarnings())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("ClassNotFoundException: A", "B");
  }

  static class StaticFinalField {
    static final int f = 1;
  }

  static class StaticNonFinalField {
    static int f = 2;
  }

  public static class A {

    @Override
    public String toString() {
      return "A";
    }
  }

  public static class B {

    @Override
    public String toString() {
      return "B";
    }
  }

  static class TestClass {

    // Simulate construction of an object (View) from a field value (R-value).
    static List<Object> getObjects(int... keys) {
      List<Object> objects = new ArrayList<>();
      for (int key : keys) {
        String name;
        if (key == 1) {
          name = "A";
        } else if (key == 2) {
          name = "B";
        } else {
          continue;
        }
        Class<TestClass> clazz = TestClass.class;
        String prefix = clazz.getName().substring(0, clazz.getName().indexOf('$') + 1);
        try {
          objects.add(clazz.forName(prefix + name).getConstructor().newInstance());
        } catch (Exception e) {
          objects.add(e.getClass().getSimpleName() + ": " + name);
        }
      }
      return objects;
    }

    public static void main(String[] args) {
      if (System.nanoTime() < 0) {
        // Force evaluation of the conditional rule for the final field type.
        System.out.println(StaticFinalField.class.getTypeName());
        System.out.println(new StaticFinalField());
      }
      // Two field references, one of which is inlined by javac.
      for (Object object : getObjects(StaticFinalField.f, StaticNonFinalField.f)) {
        System.out.println(object.toString());
      }
    }
  }
}
