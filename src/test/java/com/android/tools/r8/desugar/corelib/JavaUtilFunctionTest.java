// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class JavaUtilFunctionTest extends CoreLibDesugarTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  public JavaUtilFunctionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void checkRewrittenArguments(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    assertEquals(
        "j$.util.function.Function",
        classSubject
            .uniqueMethodWithName("applyFunction")
            .getMethod()
            .method
            .proto
            .parameters
            .values[0]
            .toSourceString());
  }

  @Test
  public void testJavaUtilOptional() throws Exception {
    String expectedOutput = StringUtils.lines("Hello, world", "Hello, world");
    testForD8()
        .addInnerClasses(JavaUtilFunctionTest.class)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .setMinApi(parameters.getRuntime())
        .addOptionsModification(this::configureCoreLibDesugar)
        .compile()
        .inspect(this::checkRewrittenArguments)
        .addRunClasspathFiles(buildDesugaredLibrary(parameters.getRuntime()))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  static class TestClass {

    private static String applyFunction(Function<String, String> f) {
      return f.apply("Hello, world");
    }

    public static void main(String[] args) {
      System.out.println(applyFunction(s -> s + System.lineSeparator() + s));
    }
  }
}
