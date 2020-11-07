// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.keepclassmembers;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepInterfaceMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public KeepInterfaceMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testIProguard() throws CompilationFailedException, IOException, ExecutionException {
    testForProguard()
        // TODO(b/159694276): Run the resulting code on runtime.
        .addProgramClasses(I.class)
        .addKeepRules(
            "-keepclassmembers class " + I.class.getTypeName() + " { void foo(); }",
            "-keep class " + I.class.getTypeName() + " { }",
            "-dontwarn")
        .compile()
        .inspect(this::inspectIClassAndMethodIsPresent);
  }

  @Test
  public void testIR8() throws CompilationFailedException, IOException, ExecutionException {
    // TODO(b/159694276): Add compat variant of this.
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class)
        .addKeepRules("-keepclassmembers class " + I.class.getTypeName() + " { void foo(); }")
        .addKeepRules("-keep class " + I.class.getTypeName() + " { }")
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspectIClassAndMethodIsPresent);
  }

  @Test
  public void testAProguard() throws CompilationFailedException, IOException, ExecutionException {
    assumeTrue(parameters.isCfRuntime());
    testForProguard()
        .addProgramClasses(I.class, A.class, B.class, Main.class)
        .addKeepRules(
            "-keepclassmembers class " + I.class.getTypeName() + " { void foo(); }", "-dontwarn")
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.foo")
        .inspect(this::inspectIClassAndMethodIsPresent);
  }

  @Test
  public void testAR8() throws CompilationFailedException, IOException, ExecutionException {
    // TODO(b/159694276): Add non-compat variant of this.
    testForR8Compat(parameters.getBackend())
        .addProgramClasses(I.class, A.class, B.class, Main.class)
        .addKeepRules("-keepclassmembers class " + I.class.getTypeName() + " { void foo(); }")
        .addKeepMainRule(Main.class)
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.foo")
        .inspect(this::inspectIClassAndMethodIsPresent);
  }

  private void inspectIClassAndMethodIsPresent(CodeInspector inspector) {
    checkClassAndMethodIsPresent(inspector, I.class);
  }

  private void checkClassAndMethodIsPresent(CodeInspector inspector, Class<?> clazz) {
    ClassSubject clazzSubject = inspector.clazz(clazz);
    assertThat(clazzSubject, isPresent());
    MethodSubject foo = clazzSubject.uniqueMethodWithName("foo");
    assertThat(foo, isPresentAndNotRenamed());
  }

  public interface I {
    void foo();
  }

  public static class A implements I {
    @Override
    public void foo() {
      System.out.println("A.foo");
    }
  }

  @NoHorizontalClassMerging
  public static class B implements I {

    @Override
    public void foo() {
      System.out.println("B.foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      runI(args.length == 0 ? new A() : new B());
    }

    private static void runI(I i) {
      i.foo();
    }
  }
}
