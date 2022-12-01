// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MemberRebindingFrontierTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addProgramClasses(Main.class, ProgramClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryClasses(Base.class, I.class)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .apply(this::setupRunclasspath)
        .run(parameters.getRuntime(), Main.class)
        .apply(result -> checkOutput(result, false));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, ProgramClass.class)
        .addProgramClassFileData()
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryClasses(I.class)
        .addLibraryClassFileData(removeFooMethod(Base.class))
        .setMinApi(parameters.getApiLevel())
        .addKeepClassRules(ProgramClass.class)
        .addKeepMainRule(Main.class)
        .compile()
        .apply(this::setupRunclasspath)
        .inspect(
            inspector -> {
              // TODO(b/254510678): We should not rebind to I.foo
              ClassSubject mainClass = inspector.clazz(Main.class);
              assertThat(mainClass, isPresent());
              MethodSubject foo = mainClass.mainMethod();
              assertThat(
                  foo, CodeMatchers.invokesMethodWithHolderAndName(typeName(I.class), "foo"));
            })
        .run(parameters.getRuntime(), Main.class)
        .apply(result -> checkOutput(result, true));
  }

  private byte[] removeFooMethod(Class<?> clazz) throws Exception {
    return transformer(clazz).removeMethodsWithName("foo").transform();
  }

  private void setupRunclasspath(TestCompileResult<?, ?> compileResult) {
    compileResult
        .applyIf(
            parameters.canUseDefaultAndStaticInterfaceMethods(),
            result ->
                result
                    .addRunClasspathClasses(I.class)
                    .addRunClasspathClassFileData(removeFooMethod(Base.class)))
        .applyIf(
            !parameters.canUseDefaultAndStaticInterfaceMethods(),
            result ->
                result
                    .addRunClasspathClasses(Base.class)
                    .addRunClasspathClassFileData(removeFooMethod(I.class)));
  }

  private void checkOutput(SingleTestRunResult<?> runResult, boolean r8) {
    if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
      runResult.assertSuccessWithOutputLines("I::foo");
      return;
    }
    // TODO(b/254510678): We should not rebind to I.foo
    if (r8) {
      runResult.assertFailureWithErrorThatThrows(NoSuchMethodError.class);
    } else {
      runResult.assertSuccessWithOutputLines("Base::foo");
    }
  }

  private interface I {

    // Introduced at a later api level.
    default void foo() {
      System.out.println("I::foo");
    }
  }

  public abstract static class Base {

    // Was present until moved into I at some api level.
    public void foo() {
      System.out.println("Base::foo");
    }
  }

  public static class ProgramClass extends Base implements I {}

  public static class Main {

    public static void main(String[] args) {
      callFoo(new ProgramClass());
    }

    private static void callFoo(ProgramClass clazz) {
      clazz.foo();
    }
  }
}
