// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class JavaTimeTest extends CoreLibDesugarTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public JavaTimeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void checkRewrittenInvokes(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    Iterator<InvokeInstructionSubject> iterator =
        classSubject.uniqueMethodWithName("main").iterateInstructions(InstructionSubject::isInvoke);
    InvokeInstructionSubject invoke = iterator.next();
    assertTrue(invoke.holder().is("j$.time.Clock"));
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Hello, world");
    testForD8()
        .addInnerClasses(JavaTimeTest.class)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .setMinApi(parameters.getRuntime())
        .addOptionsModification(this::configureCoreLibDesugarForProgramCompilation)
        .compile()
        .inspect(this::checkRewrittenInvokes)
        .addRunClasspathFiles(buildDesugaredLibrary(parameters.getApiLevel()))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  static class TestClass {

    public static void main(String[] args) {
      java.time.Clock.systemDefaultZone();
      System.out.println("Hello, world");
    }
  }
}
