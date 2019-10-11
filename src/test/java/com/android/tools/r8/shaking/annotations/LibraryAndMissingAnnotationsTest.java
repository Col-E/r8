// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.BaseCompilerCommand;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryAndMissingAnnotationsTest extends TestBase {

  private final TestParameters parameters;
  private final boolean includeOnLibraryPath;

  @Parameters(name = "{0}, includeOnLibraryPath: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  private static Function<TestParameters, Path> compilationResults =
      memoizeFunction(LibraryAndMissingAnnotationsTest::compileLibraryAnnotationToRuntime);

  private static Path compileLibraryAnnotationToRuntime(TestParameters parameters)
      throws CompilationFailedException, IOException {
    return testForR8(getStaticTemp(), parameters.getBackend())
        .addProgramClasses(LibraryAnnotation.class)
        .addKeepClassAndMembersRulesWithAllowObfuscation(LibraryAnnotation.class)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .writeToZip();
  }

  public LibraryAndMissingAnnotationsTest(TestParameters parameters, boolean includeOnLibraryPath) {
    this.parameters = parameters;
    this.includeOnLibraryPath = includeOnLibraryPath;
  }

  @Test
  public void testMainWithUseR8() throws Exception {
    runTest(testForR8(parameters.getBackend()), MainWithUse.class);
  }

  @Test
  public void testMainWithUseR8Compat() throws Exception {
    runTest(testForR8Compat(parameters.getBackend()), MainWithUse.class);
  }

  @Test
  public void testMainWithUseProguard() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    runTest(testForProguard(), MainWithUse.class);
  }

  @Test
  public void testMainWithoutUseR8() throws Exception {
    runTest(testForR8(parameters.getBackend()), MainWithoutUse.class);
  }

  @Test
  public void testMainWithoutUseR8Compat() throws Exception {
    runTest(testForR8Compat(parameters.getBackend()), MainWithoutUse.class);
  }

  @Test
  public void testMainWithoutUseProguard() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    runTest(testForProguard(), MainWithoutUse.class);
  }

  private <
          C extends BaseCompilerCommand,
          B extends BaseCompilerCommand.Builder<C, B>,
          CR extends TestCompileResult<CR, RR>,
          RR extends TestRunResult<RR>,
          T extends TestShrinkerBuilder<C, B, CR, RR, T>>
      void runTest(TestShrinkerBuilder<C, B, CR, RR, T> builder, Class<?> mainClass)
          throws Exception {
    T t =
        builder
            .addProgramClasses(Foo.class, mainClass)
            .addKeepAttributes("*Annotation*")
            .addLibraryFiles(runtimeJar(parameters))
            .addKeepClassAndMembersRules(Foo.class)
            .addKeepRules("-dontwarn " + LibraryAndMissingAnnotationsTest.class.getTypeName())
            .addKeepMainRule(mainClass)
            .setMinApi(parameters.getApiLevel());
    if (includeOnLibraryPath) {
      t.addLibraryClasses(LibraryAnnotation.class);
    } else {
      t.addKeepRules("-dontwarn " + LibraryAnnotation.class.getTypeName());
    }
    t.compile()
        .addRunClasspathFiles(compilationResults.apply(parameters))
        .run(parameters.getRuntime(), mainClass)
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(Foo.class);
              assertThat(clazz, isPresent());
              assertThat(clazz.annotation(LibraryAnnotation.class.getTypeName()), isPresent());
              MethodSubject foo = clazz.uniqueMethodWithName("foo");
              assertThat(foo, isPresent());
              assertThat(foo.annotation(LibraryAnnotation.class.getTypeName()), isPresent());
              assertFalse(foo.getMethod().parameterAnnotationsList.isEmpty());
              assertEquals(
                  LibraryAnnotation.class.getTypeName(),
                  foo.getMethod()
                      .parameterAnnotationsList
                      .get(0)
                      .annotations[0]
                      .getAnnotationType()
                      .toSourceString());
              assertThat(
                  clazz
                      .uniqueFieldWithName("bar")
                      .annotation(LibraryAnnotation.class.getTypeName()),
                  isPresent());
            })
        .assertSuccessWithOutputLines("Hello World!");
  }

  @Test
  public void testMainWithoutUse() {}

  @Retention(RetentionPolicy.RUNTIME)
  @interface LibraryAnnotation {}

  @LibraryAnnotation
  public static class Foo {

    @LibraryAnnotation public String bar = "Hello World!";

    @LibraryAnnotation
    public void foo(@LibraryAnnotation String arg) {
      System.out.println(arg);
    }
  }

  public static class MainWithoutUse {

    public static void main(String[] args) {
      Foo foo = new Foo();
      foo.foo(foo.bar);
    }
  }

  public static class MainWithUse {
    public static void main(String[] args) {
      if (args.length > 0 && args[0].equals(LibraryAnnotation.class.getTypeName())) {
        System.out.print("This will never be printed");
      }
      Foo foo = new Foo();
      foo.foo(foo.bar);
    }
  }
}
