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
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector.QueryNode;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptByTwoRulesTestRunner extends TestBase {

  private static final Class<?> CLASS = KeptByTwoRulesTest.class;
  private static final Collection<Class<?>> CLASSES = Arrays.asList(CLASS);

  private final String EXPECTED = StringUtils.lines("called foo");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(CLASSES)
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void test() throws Exception {
    MethodReference mainMethod = methodFromMethod(CLASS.getDeclaredMethod("main", String[].class));
    MethodReference fooMethod = methodFromMethod(CLASS.getDeclaredMethod("foo"));

    String keepPublicRule = "-keep @com.android.tools.r8.Keep class * {  public *; }";
    String keepFooRule = "-keep class " + CLASS.getTypeName() + " { public void foo(); }";
    GraphInspector inspector =
        testForR8(parameters.getBackend())
            .enableGraphInspector()
            .addProgramClasses(CLASSES)
            .addKeepAnnotation()
            .addKeepRules(keepPublicRule, keepFooRule)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), CLASS)
            .assertSuccessWithOutput(EXPECTED)
            .graphInspector();

    assertEquals(2, inspector.getRoots().size());
    QueryNode keepPublic = inspector.rule(keepPublicRule).assertRoot();
    QueryNode keepFoo = inspector.rule(keepFooRule).assertRoot();

    inspector
        .method(mainMethod)
        .assertNotRenamed()
        .assertKeptBy(keepPublic)
        .assertNotKeptBy(keepFoo);

    // Check foo is called from main and kept by two rules.
    inspector
        .method(fooMethod)
        .assertNotRenamed()
        .assertInvokedFrom(mainMethod)
        .assertKeptBy(keepPublic)
        .assertKeptBy(keepFoo);

    // Check the class is also kept by both rules.
    inspector
        .clazz(classFromClass(CLASS))
        .assertNotRenamed()
        .assertKeptBy(keepPublic)
        .assertKeptBy(keepFoo);
  }
}
