// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import static com.android.tools.r8.references.Reference.methodFromMethod;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
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
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptByAnnotatedMethodTestRunner extends TestBase {

  private static final Class<?> CLASS = KeptByAnnotatedMethodTest.class;
  private static final Class<?> INNER = KeptByAnnotatedMethodTest.Inner.class;
  private static final Collection<Class<?>> CLASSES = Arrays.asList(CLASS, INNER);

  private final String EXPECTED = StringUtils.lines("called bar");

  private final Backend backend;

  @Parameters(name = "{0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public KeptByAnnotatedMethodTestRunner(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testKeptMethod() throws Exception {
    MethodReference mainMethod = methodFromMethod(CLASS.getDeclaredMethod("main", String[].class));
    MethodReference fooMethod = methodFromMethod(INNER.getDeclaredMethod("foo"));
    MethodReference barMethod = methodFromMethod(INNER.getDeclaredMethod("bar"));
    MethodReference bazMethod = methodFromMethod(INNER.getDeclaredMethod("baz"));

    if (backend == Backend.CF) {
      testForJvm().addProgramClasses(CLASSES).run(CLASS).assertSuccessWithOutput(EXPECTED);
    }

    Origin ruleOrigin = Origin.unknown();

    String keepAnnotatedMethodsRule = "-keepclassmembers class * { @com.android.tools.r8.Keep *; }";
    String keepClassesOfAnnotatedMethodsRule =
        "-keep,allowobfuscation class * { <init>(); @com.android.tools.r8.Keep *; }";
    GraphInspector inspector =
        testForR8(backend)
            .enableGraphInspector()
            .enableInliningAnnotations()
            .addProgramClasses(CLASSES)
            .addKeepMainRule(CLASS)
            .addKeepRules(keepAnnotatedMethodsRule, keepClassesOfAnnotatedMethodsRule)
            .run(CLASS)
            .assertSuccessWithOutput(EXPECTED)
            .graphInspector();

    assertEquals(3, inspector.getRoots().size());
    QueryNode keepMain = inspector.rule(ruleOrigin, 1, 1).assertRoot();
    QueryNode keepAnnotatedMethods = inspector.rule(keepAnnotatedMethodsRule).assertRoot();
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
