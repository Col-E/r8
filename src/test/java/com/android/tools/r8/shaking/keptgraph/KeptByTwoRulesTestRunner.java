// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import static com.android.tools.r8.references.Reference.classFromClass;
import static com.android.tools.r8.references.Reference.methodFromMethod;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.references.MethodReference;
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
public class KeptByTwoRulesTestRunner extends TestBase {

  private static final Class<?> CLASS = KeptByTwoRulesTest.class;
  private static final Collection<Class<?>> CLASSES = Arrays.asList(CLASS);

  private final String EXPECTED = StringUtils.lines("called foo");

  private final Backend backend;

  @Parameters(name = "{0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public KeptByTwoRulesTestRunner(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    MethodReference mainMethod = methodFromMethod(CLASS.getDeclaredMethod("main", String[].class));
    MethodReference fooMethod = methodFromMethod(CLASS.getDeclaredMethod("foo"));

    if (backend == Backend.CF) {
      testForJvm().addProgramClasses(CLASSES).run(CLASS).assertSuccessWithOutput(EXPECTED);
    }

    String keepPublicRule = "-keep @com.android.tools.r8.Keep class * {  public *; }";
    String keepFooRule = "-keep class " + CLASS.getTypeName() + " { public void foo(); }";
    GraphInspector inspector =
        testForR8(backend)
            .enableGraphInspector()
            .enableInliningAnnotations()
            .addProgramClasses(CLASSES)
            .addKeepRules(keepPublicRule, keepFooRule)
            .run(CLASS)
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
