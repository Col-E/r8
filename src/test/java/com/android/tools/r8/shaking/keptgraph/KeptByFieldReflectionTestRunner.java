// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import static com.android.tools.r8.references.Reference.fieldFromField;
import static com.android.tools.r8.references.Reference.methodFromMethod;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.FieldReference;
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
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptByFieldReflectionTestRunner extends TestBase {

  private static final Class<?> CLASS = KeptByFieldReflectionTest.class;
  private static final Collection<Class<?>> CLASSES = Arrays.asList(CLASS);
  private static final String TYPE_NAME = CLASS.getTypeName();

  private final String EXPECTED_STDOUT = StringUtils.lines("got foo: 42");

  private final String EXPECTED_WHYAREYOUKEEPING =
      StringUtils.lines(
          "int " + TYPE_NAME + ".foo",
          "|- is reflected from:",
          "|  void " + TYPE_NAME + ".main(java.lang.String[])",
          "|- is referenced in keep rule:",
          "|  -keep class " + TYPE_NAME + " { public static void main(java.lang.String[]); }");

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
        .assertSuccessWithOutput(EXPECTED_STDOUT);
  }

  @Test
  public void test() throws Exception {
    MethodReference mainMethod = methodFromMethod(CLASS.getDeclaredMethod("main", String[].class));
    FieldReference fooField = fieldFromField(CLASS.getDeclaredField("foo"));
    MethodReference fooInit = methodFromMethod(CLASS.getDeclaredConstructor());

    WhyAreYouKeepingConsumer consumer = new WhyAreYouKeepingConsumer(null);
    GraphInspector inspector =
        testForR8(parameters.getBackend())
            .enableGraphInspector(consumer)
            .addProgramClasses(CLASSES)
            .addKeepAnnotation()
            .addKeepMainRule(CLASS)
            .enableNoInliningOfDefaultInitializerAnnotations()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), CLASS)
            .assertSuccessWithOutput(EXPECTED_STDOUT)
            .graphInspector();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    consumer.printWhyAreYouKeeping(fooField, new PrintStream(baos));
    assertEquals(EXPECTED_WHYAREYOUKEEPING, baos.toString());

    assertEquals(1, inspector.getRoots().size());
    QueryNode keepMain = inspector.rule(Origin.unknown(), 1, 1).assertRoot();

    inspector.method(mainMethod).assertNotRenamed().assertKeptBy(keepMain);

    // The field is primarily kept by the reflective lookup in main.
    QueryNode fooNode = inspector.field(fooField).assertRenamed();
    fooNode.assertReflectedFrom(mainMethod);

    // The field is also kept by the write in Foo.<init>.
    // We may want to change that behavior. See b/124428834.
    fooNode.assertKeptBy(inspector.method(fooInit));
  }
}
