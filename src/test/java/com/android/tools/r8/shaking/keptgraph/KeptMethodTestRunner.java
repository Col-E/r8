// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import static com.android.tools.r8.references.Reference.methodFromMethod;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptMethodTestRunner extends TestBase {

  private static final Class<KeptMethodTest> CLASS = KeptMethodTest.class;
  private static final String EXPECTED = StringUtils.lines("called bar");

  private final Backend backend;

  @Parameters(name = "{0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public KeptMethodTestRunner(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testKeptMethod() throws Exception {
    MethodReference mainMethod = methodFromMethod(CLASS.getDeclaredMethod("main", String[].class));
    MethodReference barMethod = methodFromMethod(CLASS.getDeclaredMethod("bar"));
    MethodReference bazMethod = methodFromMethod(CLASS.getDeclaredMethod("baz"));

    GraphInspector inspector =
        testForR8(backend)
            .enableGraphInspector()
            .enableInliningAnnotations()
            .addProgramClasses(CLASS)
            .addKeepMethodRules(mainMethod)
            .run(CLASS)
            .assertSuccessWithOutput(EXPECTED)
            .graphInspector();

    // The only root should be the keep main-method rule.
    assertEquals(1, inspector.getRoots().size());

    // Check that the call chain goes from root -> main(unchanged) -> bar(renamed).
    inspector.method(barMethod).assertRenamed().assertInvokedFrom(mainMethod);
    inspector.method(mainMethod).assertNotRenamed().assertKeptByRootRule();

    // Check baz is removed.
    inspector.method(bazMethod).assertAbsent();
  }
}
