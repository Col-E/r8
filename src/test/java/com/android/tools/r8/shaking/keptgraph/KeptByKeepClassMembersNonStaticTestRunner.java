// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import static com.android.tools.r8.references.Reference.classFromClass;
import static com.android.tools.r8.references.Reference.methodFromMethod;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.shaking.WhyAreYouKeepingConsumer;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector.QueryNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptByKeepClassMembersNonStaticTestRunner extends TestBase {

  @NeverClassInline
  public static class TestClass {

    public static void main(String[] args) {
      new TestClass().bar();
    }

    @NeverInline
    @NeverPropagateValue
    public void bar() {
      System.out.println("called bar");
    }

    @NeverInline
    public void baz() {
      System.out.println("called baz");
    }
  }

  private static final Class<?> CLASS = TestClass.class;
  private static final String CLASS_NAME = CLASS.getTypeName();
  private static final String EXPECTED = StringUtils.lines("called bar");

  private static final String KEEP_CLASS_MEMBERS_RULE =
      "-keepclassmembers class " + TestClass.class.getTypeName() + " { void baz(); }";

  private static final String EXPECTED_WHYAREYOUKEEPING =
      StringUtils.lines(
          "void " + CLASS_NAME + ".baz()",
          "|- is referenced in keep rule:",
          "|  " + KEEP_CLASS_MEMBERS_RULE,
          "|- is satisfied with precondition:",
          "|  " + CLASS_NAME,
          "|- is referenced in keep rule:",
          "|  -keep class " + CLASS_NAME + " { public static void main(java.lang.String[]); }");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public KeptByKeepClassMembersNonStaticTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testKeptMethod() throws Exception {
    ClassReference clazz = classFromClass(CLASS);
    MethodReference mainMethod = methodFromMethod(CLASS.getDeclaredMethod("main", String[].class));
    MethodReference barMethod = methodFromMethod(CLASS.getDeclaredMethod("bar"));
    MethodReference bazMethod = methodFromMethod(CLASS.getDeclaredMethod("baz"));

    WhyAreYouKeepingConsumer whyAreYouKeepingConsumer = new WhyAreYouKeepingConsumer(null);
    GraphInspector inspector =
        testForR8(parameters.getBackend())
            .enableGraphInspector(whyAreYouKeepingConsumer)
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableMemberValuePropagationAnnotations()
            .addProgramClasses(CLASS)
            .addKeepMainRule(CLASS)
            .addKeepRules(KEEP_CLASS_MEMBERS_RULE)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), CLASS)
            .assertSuccessWithOutput(EXPECTED)
            .graphInspector();

    // The only root should be the keep annotation rule.
    assertEquals(1, inspector.getRoots().size());
    QueryNode keepMainRule = inspector.rule(Origin.unknown(), 1, 1).assertRoot();
    QueryNode keepClassMembersRule = inspector.rule(KEEP_CLASS_MEMBERS_RULE).assertNotRoot();

    // Check that the call chain goes from root -> main(unchanged) -> bar(renamed).
    inspector.method(barMethod).assertRenamed().assertInvokedFrom(mainMethod);
    inspector.method(mainMethod).assertNotRenamed().assertKeptBy(keepMainRule);

    // Check baz is kept by the dependent rule.
    inspector.method(bazMethod).assertNotRenamed().assertKeptBy(keepClassMembersRule);

    // Regression for b/131822600, the dependent rule must be satisfied by Bar being kept.
    keepClassMembersRule.assertSatisfiedBy(inspector.clazz(clazz));

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    whyAreYouKeepingConsumer.printWhyAreYouKeeping(bazMethod, new PrintStream(baos));

    assertEquals(EXPECTED_WHYAREYOUKEEPING, baos.toString());
  }
}
