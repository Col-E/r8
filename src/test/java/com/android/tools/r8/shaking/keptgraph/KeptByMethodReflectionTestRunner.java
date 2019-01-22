// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import static com.android.tools.r8.references.Reference.methodFromMethod;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.shaking.WhyAreYouKeepingConsumer;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector.QueryNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptByMethodReflectionTestRunner extends TestBase {

  private static final Class<?> CLASS = KeptByMethodReflectionTest.class;
  private static final Collection<Class<?>> CLASSES = Arrays.asList(CLASS);

  private final String EXPECTED_STDOUT = StringUtils.lines("called foo");

  private final String EXPECTED_WHYAREYOUKEEPING =
      StringUtils.lines(
          "void com.android.tools.r8.shaking.keptgraph.KeptByMethodReflectionTest.foo()",
          "|- is reflected from:",
          "|  void com.android.tools.r8.shaking.keptgraph.KeptByMethodReflectionTest.main(java.lang.String[])",
          "|- is referenced in keep rule:",
          "|  -keep class com.android.tools.r8.shaking.keptgraph.KeptByMethodReflectionTest { public static void main(java.lang.String[]); }");

  private final Backend backend;

  @Parameters(name = "{0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public KeptByMethodReflectionTestRunner(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    MethodReference mainMethod = methodFromMethod(CLASS.getDeclaredMethod("main", String[].class));
    MethodReference fooMethod = methodFromMethod(CLASS.getDeclaredMethod("foo"));

    if (backend == Backend.CF) {
      testForJvm().addProgramClasses(CLASSES).run(CLASS).assertSuccessWithOutput(EXPECTED_STDOUT);
    }

    WhyAreYouKeepingConsumer consumer = new WhyAreYouKeepingConsumer(null);
    GraphInspector inspector =
        testForR8(backend)
            .enableGraphInspector(consumer)
            .enableInliningAnnotations()
            .addProgramClasses(CLASSES)
            .addKeepMainRule(CLASS)
            .run(CLASS)
            .assertSuccessWithOutput(EXPECTED_STDOUT)
            .graphInspector();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    consumer.printWhyAreYouKeeping(fooMethod, new PrintStream(baos));
    assertEquals(EXPECTED_WHYAREYOUKEEPING, baos.toString());

    assertEquals(1, inspector.getRoots().size());
    QueryNode keepMain = inspector.rule(Origin.unknown(), 1, 1).assertRoot();

    inspector.method(mainMethod).assertNotRenamed().assertKeptBy(keepMain);

    inspector.method(fooMethod).assertRenamed().assertReflectedFrom(mainMethod);
  }
}
