// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import static com.android.tools.r8.references.Reference.classFromClass;
import static com.android.tools.r8.references.Reference.methodFromMethod;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector.QueryNode;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RemovedClassTestRunner extends TestBase {

  private static final Class<?> CLASS = RemovedClassTest.class;
  private static final Class<?> REMOVED_CLASS = RemovedClassTest.RemovedInnerClass.class;
  private static final List<Class<?>> CLASSES = ImmutableList.of(CLASS, REMOVED_CLASS);

  private static final String EXPECTED = StringUtils.lines("called bar");

  private final Backend backend;

  @Parameters(name = "{0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public RemovedClassTestRunner(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testRemovedClass() throws Exception {
    MethodReference mainMethod = methodFromMethod(CLASS.getDeclaredMethod("main", String[].class));
    MethodReference barMethod = methodFromMethod(CLASS.getDeclaredMethod("bar"));
    MethodReference bazMethod = methodFromMethod(CLASS.getDeclaredMethod("baz"));

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    R8TestCompileResult compileResult =
        testForR8(backend)
            .enableGraphInspector()
            .enableInliningAnnotations()
            .addProgramClasses(CLASSES)
            .addKeepMethodRules(mainMethod)
            .addKeepRules("-whyareyoukeeping class " + REMOVED_CLASS.getTypeName())
            .redirectStdOut(new PrintStream(baos))
            .compile();
    String expectedOutput = StringUtils.lines("Nothing is keeping " + REMOVED_CLASS.getTypeName());
    String compileOutput = new String(baos.toByteArray(), StandardCharsets.UTF_8);
    assertEquals(expectedOutput, compileOutput);

    GraphInspector inspector =
        compileResult.run(CLASS).assertSuccessWithOutput(EXPECTED).graphInspector();

    // The only root should be the keep main-method rule.
    assertEquals(1, inspector.getRoots().size());
    QueryNode root = inspector.rule(Origin.unknown(), 1, 1).assertRoot();

    // Check that the call chain goes from root -> main(unchanged) -> bar(renamed).
    inspector.method(barMethod).assertRenamed().assertInvokedFrom(mainMethod);
    inspector.method(mainMethod).assertNotRenamed().assertKeptBy(root);

    // Check baz is removed.
    inspector.method(bazMethod).assertAbsent();

    // Check that the inner class is removed.
    inspector.clazz(classFromClass(REMOVED_CLASS)).assertAbsent();
  }
}
