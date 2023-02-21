// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import static com.android.tools.r8.references.Reference.classFromClass;
import static com.android.tools.r8.references.Reference.methodFromMethod;
import static org.junit.Assert.assertEquals;

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
public class KeptByKeepClassMembersTestRunner extends TestBase {

  private static final Class<?> CLASS = KeptByKeepClassMembersTest.class;
  private static final String PKG = CLASS.getPackage().getName();
  private final String EXPECTED = StringUtils.lines("called bar");
  private final String EXPECTED_WHYAREYOUKEEPING =
      StringUtils.lines(
          "void " + PKG + ".KeptByKeepClassMembersTest.baz()",
          "|- is referenced in keep rule:",
          "|  -keepclassmembers class "
              + PKG
              + ".KeptByKeepClassMembersTest { static void baz(); }",
          "|- is satisfied with precondition:",
          "|  " + PKG + ".KeptByKeepClassMembersTest",
          "|- is referenced in keep rule:",
          "|  -keep class "
              + PKG
              + ".KeptByKeepClassMembersTest"
              + " { public static void main(java.lang.String[]); }");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public KeptByKeepClassMembersTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testKeptMethod() throws Exception {
    ClassReference clazz = classFromClass(CLASS);
    MethodReference mainMethod = methodFromMethod(CLASS.getDeclaredMethod("main", String[].class));
    MethodReference barMethod = methodFromMethod(CLASS.getDeclaredMethod("bar"));
    MethodReference bazMethod = methodFromMethod(CLASS.getDeclaredMethod("baz"));

    String keepClassMembersRuleContent =
        "-keepclassmembers class "
            + KeptByKeepClassMembersTest.class.getTypeName()
            + " { static void baz(); }";

    WhyAreYouKeepingConsumer whyAreYouKeepingConsumer = new WhyAreYouKeepingConsumer(null);
    GraphInspector inspector =
        testForR8(parameters.getBackend())
            .enableGraphInspector(whyAreYouKeepingConsumer)
            .enableInliningAnnotations()
            .addProgramClasses(CLASS)
            .addKeepMainRule(KeptByKeepClassMembersTest.class)
            .addKeepRules(keepClassMembersRuleContent)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), CLASS)
            .assertSuccessWithOutput(EXPECTED)
            .graphInspector();

    // The only root should be the keep main rule.
    assertEquals(1, inspector.getRoots().size());
    QueryNode keepMainRule = inspector.rule(Origin.unknown(), 1, 1).assertRoot();
    QueryNode keepClassMembersRule = inspector.rule(keepClassMembersRuleContent).assertNotRoot();

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
