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
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector.QueryNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptByConditionalOnMethodTest extends TestBase {

  public static class IfClass {
    public void foo(String name) throws Exception {
      Class<?> clazz = Class.forName(name);
      Object object = clazz.getDeclaredConstructor().newInstance();
      clazz.getDeclaredMethod("bar").invoke(object);
    }
  }

  public static class ThenClass {
    public void bar() {
      System.out.println("ThenClass.bar()!");
    }
  }

  public static class Main {
    public static void main(String[] args) throws Exception {
      new IfClass().foo(args[0]);
    }
  }

  private final String EXPECTED = StringUtils.lines("ThenClass.bar()!");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public KeptByConditionalOnMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testKeptMethod() throws Exception {
    MethodReference mainMethod =
        methodFromMethod(Main.class.getDeclaredMethod("main", String[].class));
    MethodReference fooMethod =
        methodFromMethod(IfClass.class.getDeclaredMethod("foo", String.class));
    MethodReference barMethod = methodFromMethod(ThenClass.class.getDeclaredMethod("bar"));

    String ifRuleContent =
        "-if class "
            + IfClass.class.getTypeName()
            + " { public *; }"
            + " -keep class "
            + ThenClass.class.getTypeName()
            + " { public *; }";
    GraphInspector inspector =
        testForR8(parameters.getBackend())
            .enableGraphInspector()
            .addProgramClasses(Main.class, IfClass.class, ThenClass.class)
            .addKeepMainRule(Main.class)
            .addKeepRules(ifRuleContent)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), Main.class, ThenClass.class.getTypeName())
            .assertSuccessWithOutput(EXPECTED)
            .graphInspector();

    // The only root should be the keep annotation rule.
    assertEquals(1, inspector.getRoots().size());
    QueryNode root = inspector.rule(Origin.unknown(), 1, 1).assertRoot();

    // Check that the call chain goes from root -> main(unchanged) -> foo(renamed).
    inspector.method(fooMethod).assertRenamed().assertInvokedFrom(mainMethod);
    inspector.method(mainMethod).assertNotRenamed().assertKeptBy(root);

    // Check bar is kept and not renamed.
    QueryNode barNode = inspector.method(barMethod).assertNotRenamed();

    // Check the if rule was triggered once by IfClass and is keeping bar alive.
    QueryNode ifClassNode = inspector.clazz(classFromClass(IfClass.class)).assertPresent();
    inspector
        .ruleInstances(ifRuleContent)
        .assertSize(1)
        // TODO(b/141093535): Should the precondition set contain the public member too?
        .assertAllMatch(instance -> instance.isSatisfiedBy(ifClassNode))
        .assertAllMatch(barNode::isKeptBy);

    // Finally check that ThenClass is *not* a reason that bar is kept.
    QueryNode thenClassNode = inspector.clazz(classFromClass(ThenClass.class)).assertPresent();
    barNode.assertNotKeptBy(thenClassNode);
  }
}
