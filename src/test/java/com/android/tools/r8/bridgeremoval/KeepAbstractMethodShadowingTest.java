// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bridgeremoval;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepAbstractMethodShadowingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public KeepAbstractMethodShadowingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(A.class, C.class, Main.class)
        .addProgramClassFileData(getBWithAbstractFoo())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(AbstractMethodError.class);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, C.class, Main.class)
        .addProgramClassFileData(getBWithAbstractFoo())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(A.class)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(AbstractMethodError.class)
        .inspectFailure(
            inspector -> {
              ClassSubject clazz = inspector.clazz(B.class);
              assertThat(clazz, isPresent());
              MethodSubject foo = clazz.uniqueMethodWithOriginalName("foo");
              assertThat(foo, isPresent());
            });
  }

  private byte[] getBWithAbstractFoo() throws Exception {
    return transformer(B.class)
        .renameMethod(MethodPredicate.onName("will_be_foo"), "foo")
        .transform();
  }

  public static class A {
    public void foo() {
      System.out.println("Hello World");
    }
  }

  public abstract static class B extends A {
    public abstract void /* foo */ will_be_foo();
  }

  public static class C extends B {

    @Override
    public void foo() {
      super.foo();
    }

    @Override
    public void will_be_foo() {}
  }

  public static class Main {

    public static void main(String[] args) {
      new C().foo();
    }
  }
}
