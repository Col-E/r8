// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.methods.interfaces;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This tests is showing the issue filed in b/143590191. The expectations for the test should
 * reflect the decisions to keep the interface method or not in the super interface.
 */
@RunWith(Parameterized.class)
public class DefaultInterfaceMethodsTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DefaultInterfaceMethodsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testSingleInheritanceProguard() throws CompilationFailedException, IOException {
    assumeTrue(parameters.isCfRuntime());
    testForProguard()
        .addProgramClasses(I.class, J.class)
        .setMinApi(parameters)
        .addKeepMethodRules(J.class, "void foo()")
        .addKeepRules("-dontwarn")
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(J.class);
              assertThat(clazz, isPresent());
              assertThat(clazz.uniqueMethodWithOriginalName("foo"), not(isPresent()));
              assertThat(inspector.clazz(I.class), not(isPresent()));
            });
  }

  @Test
  public void testSingleInheritanceR8BeforeNougat()
      throws CompilationFailedException, IOException, ExecutionException {
    assumeTrue(parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(AndroidApiLevel.N));
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(I.class, J.class)
            .setMinApi(parameters)
            .addKeepMethodRules(J.class, "void foo()")
            .addOptionsModification(options -> options.getVerticalClassMergerOptions().disable())
            .addDontObfuscate()
            .compile();
    // TODO(b/144269679): We should be able to compile and run this.
    testForR8(parameters.getBackend())
        .addProgramClasses(ImplJ.class, Main.class)
        .addClasspathClasses(I.class, J.class)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addKeepClassRules(ImplJ.class)
        .addRunClasspathFiles(compileResult.writeToZip())
        .run(parameters.getRuntime(), Main.class, ImplJ.class.getTypeName(), "foo")
        .assertFailureWithErrorThatMatches(
            containsString(
                "com.android.tools.r8.shaking.methods.interfaces.DefaultInterfaceMethodsTest$I$-CC"));
  }

  @Test
  public void testSingleInheritanceR8OnNougatAndForward()
      throws CompilationFailedException, IOException, ExecutionException {
    assumeTrue(
        parameters.isCfRuntime()
            || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N));
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(I.class, J.class)
            .setMinApi(parameters)
            .addKeepMethodRules(J.class, "void foo()")
            .compile();
    testForRuntime(parameters)
        .addProgramClasses(ImplJ.class, Main.class)
        .addRunClasspathFiles(compileResult.writeToZip())
        .run(parameters.getRuntime(), Main.class, ImplJ.class.getTypeName(), "foo")
        .assertSuccessWithOutputLines("Hello World!");
  }

  @Test
  public void testKeepInterfaceMethodOnSubInterface()
      throws CompilationFailedException, IOException, ExecutionException {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(I.class, J.class, ImplJ.class)
            .setMinApi(parameters)
            .addKeepClassAndMembersRules(ImplJ.class)
            .addKeepMethodRules(J.class, "void foo()")
            .compile();
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .addRunClasspathFiles(compileResult.writeToZip())
        .run(parameters.getRuntime(), Main.class, ImplJ.class.getTypeName(), "foo")
        .assertSuccessWithOutputLines("Hello World!");
  }

  @Test
  public void testKeepInterfaceMethodOnImplementingType()
      throws CompilationFailedException, IOException, ExecutionException {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(I.class, J.class, ImplJ.class, SubImplJ.class)
            .setMinApi(parameters)
            .addKeepMethodRules(SubImplJ.class, "void <init>()", "void foo()")
            .compile();
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .addRunClasspathFiles(compileResult.writeToZip())
        .run(parameters.getRuntime(), Main.class, SubImplJ.class.getTypeName(), "foo")
        .assertSuccessWithOutputLines("Hello World!");
  }

  public interface I {

    default void foo() {
      System.out.println("Hello World!");
    }
  }

  public interface J extends I {}

  public static class ImplJ implements J {}

  public static class SubImplJ extends ImplJ {}

  public static class Main {

    public static void main(String[] args) throws Exception {
      Object o = Class.forName(args[0]).getDeclaredConstructor().newInstance();
      o.getClass().getMethod(args[1]).invoke(o);
    }
  }
}
