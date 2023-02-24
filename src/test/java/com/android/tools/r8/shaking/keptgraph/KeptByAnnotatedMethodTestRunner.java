// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import static com.android.tools.r8.references.Reference.methodFromMethod;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
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
public class KeptByAnnotatedMethodTestRunner extends TestBase {

  private static final Class<?> CLASS = KeptByAnnotatedMethodTest.class;
  private static final Class<?> INNER = KeptByAnnotatedMethodTest.Inner.class;
  private static final Collection<Class<?>> CLASSES = Arrays.asList(CLASS, INNER);

  private final String EXPECTED = StringUtils.lines("called bar");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters).addProgramClasses(CLASSES).run(CLASS).assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testKeptMethod() throws Exception {
    MethodReference mainMethod = methodFromMethod(CLASS.getDeclaredMethod("main", String[].class));
    MethodReference fooMethod = methodFromMethod(INNER.getDeclaredMethod("foo"));
    MethodReference barMethod = methodFromMethod(INNER.getDeclaredMethod("bar"));
    MethodReference bazMethod = methodFromMethod(INNER.getDeclaredMethod("baz"));

    Origin ruleOrigin = Origin.unknown();

    String keepAnnotatedMethodsRule = "-keepclassmembers class * { @com.android.tools.r8.Keep *; }";
    String keepClassesOfAnnotatedMethodsRule =
        "-keep,allowobfuscation class * { <init>(); @com.android.tools.r8.Keep *; }";
    GraphInspector inspector =
        testForR8(parameters.getBackend())
            .enableGraphInspector()
            .enableInliningAnnotations()
            .addProgramClasses(CLASSES)
            .addKeepAnnotation()
            .addKeepMainRule(CLASS)
            .addKeepRules(keepAnnotatedMethodsRule, keepClassesOfAnnotatedMethodsRule)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), CLASS)
            .assertSuccessWithOutput(EXPECTED)
            .graphInspector();

    assertEquals(2, inspector.getRoots().size());
    QueryNode keepMain = inspector.rule(ruleOrigin, 1, 1).assertRoot();
    QueryNode keepAnnotatedMethods = inspector.rule(keepAnnotatedMethodsRule).assertNotRoot();
    QueryNode keepClassesOfAnnotatedMethods =
        inspector.rule(keepClassesOfAnnotatedMethodsRule).assertRoot();

    inspector.method(mainMethod).assertNotRenamed().assertKeptBy(keepMain);

    // Check that Inner is allowed and is actually renamed.
    inspector.clazz(Reference.classFromClass(INNER)).assertRenamed();

    // Check bar is called from foo.
    inspector.method(barMethod).assertRenamed().assertInvokedFrom(fooMethod);

    // Check foo *is not* called from main (it is reflectively accessed) and check that it is kept.
    inspector
        .method(fooMethod)
        .assertNotRenamed()
        .assertNotInvokedFrom(mainMethod)
        .assertKeptBy(keepAnnotatedMethods)
        .assertKeptBy(keepClassesOfAnnotatedMethods);

    // Check baz is removed.
    inspector.method(bazMethod).assertAbsent();
  }
}
