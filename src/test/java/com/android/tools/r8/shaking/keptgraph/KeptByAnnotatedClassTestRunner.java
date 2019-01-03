// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import static com.android.tools.r8.references.Reference.methodFromMethod;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.shaking.CollectingGraphConsumer;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptByAnnotatedClassTestRunner extends TestBase {

  private static final Class<KeptByAnnotatedClassTest> CLASS = KeptByAnnotatedClassTest.class;
  private final String EXPECTED = StringUtils.lines("called bar");

  private final Backend backend;

  @Parameters(name = "{0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public KeptByAnnotatedClassTestRunner(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testKeptMethod() throws Exception {
    MethodReference mainMethod = methodFromMethod(CLASS.getDeclaredMethod("main", String[].class));
    MethodReference barMethod = methodFromMethod(CLASS.getDeclaredMethod("bar"));
    MethodReference bazMethod = methodFromMethod(CLASS.getDeclaredMethod("baz"));

    CollectingGraphConsumer graphConsumer = new CollectingGraphConsumer(null);
    CodeInspector codeInspector =
        testForR8(backend)
            .setKeptGraphConsumer(graphConsumer)
            .enableInliningAnnotations()
            .addProgramClasses(CLASS)
            .addKeepRules("-keep @com.android.tools.r8.Keep class * { public *; }")
            .run(CLASS)
            .assertSuccessWithOutput(EXPECTED)
            .inspector();

    GraphInspector graphInspector = new GraphInspector(graphConsumer, codeInspector);

    // The only root should be the keep annotation rule.
    assertEquals(1, graphInspector.getRoots().size());

    // Check that the call chain goes from root -> main(unchanged) -> bar(renamed).
    graphInspector.method(barMethod).assertRenamed().assertInvokedFrom(mainMethod);
    graphInspector.method(mainMethod).assertNotRenamed().assertKeptByRootRule();

    // Check baz is removed.
    graphInspector.method(bazMethod).assertAbsent();
  }
}
