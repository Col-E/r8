// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8SHRINK_TR;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.R8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.R8_L8SHRINK_TR;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JavaUtilFunctionTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_OUTPUT = StringUtils.lines("Hello, world", "Hello, world");

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        getJdk8Jdk11(),
        ImmutableSet.of(D8_L8DEBUG, D8_L8SHRINK, R8_L8SHRINK, D8_L8SHRINK_TR, R8_L8SHRINK_TR));
  }

  public JavaUtilFunctionTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  private void checkRewrittenArguments(CodeInspector inspector) {
    if (!libraryDesugaringSpecification.hasEmulatedInterfaceDesugaring(parameters)) {
      return;
    }
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    String function =
        libraryDesugaringSpecification.hasJDollarFunction(parameters)
            ? "j$.util.function.Function"
            : "java.util.function.Function";
    assertEquals(
        function,
        classSubject
            .uniqueMethodWithOriginalName("applyFunction")
            .getMethod()
            .getReference()
            .proto
            .parameters
            .values[0]
            .toSourceString());
  }

  @Test
  public void testJavaUtilFunction() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .noMinification()
        .enableConstantArgumentAnnotations()
        .compile()
        .inspect(this::checkRewrittenArguments)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class TestClass {

    @KeepConstantArguments
    @NeverInline
    private static String applyFunction(Function<String, String> f) {
      return f.apply("Hello, world");
    }

    public static void main(String[] args) {
      System.out.println(applyFunction(s -> s + System.lineSeparator() + s));
    }
  }
}
