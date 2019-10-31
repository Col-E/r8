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
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
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
  public void testSingleInheritanceProguard()
      throws CompilationFailedException, IOException, ExecutionException {
    assumeTrue(parameters.isCfRuntime());
    testForProguard()
        .addProgramClasses(I.class, J.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepMethodRules(J.class, "void foo()")
        .addKeepRules("-dontwarn")
        .compile()
        .inspect(DefaultInterfaceMethodsTest::inspectBaseInterfaceRemove);
  }

  @Test
  public void testSingleInheritanceR8()
      throws CompilationFailedException, IOException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class, J.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepMethodRules(J.class, "void foo()")
        .compile()
        .inspect(DefaultInterfaceMethodsTest::inspectBaseInterfaceRemove);
  }

  private static void inspectBaseInterfaceRemove(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(J.class);
    assertThat(clazz, isPresent());
    assertThat(clazz.uniqueMethodWithName("foo"), not(isPresent()));
    assertThat(inspector.clazz(I.class), not(isPresent()));
  }

  @Test
  public void testKeepInterfaceMethodOnSubInterface()
      throws CompilationFailedException, IOException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, I.class, J.class, B.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(B.class)
        .addKeepMethodRules(J.class, "void foo()")
        .run(parameters.getRuntime(), Main.class, B.class.getTypeName(), "foo")
        .assertFailureWithErrorThatMatches(containsString("NoSuchMethodException"));
  }

  @Test
  public void testKeepInterfaceMethodOnImplementingType()
      throws CompilationFailedException, IOException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, I.class, A.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepClassAndMembersRules(Main.class)
        .addKeepMethodRules(A.class, "void <init>()", "void foo()")
        .run(parameters.getRuntime(), Main.class, A.class.getTypeName(), "foo")
        .assertFailureWithErrorThatMatches(containsString("NoSuchMethodException"));
  }

  public interface I {

    default void foo() {
      System.out.println("Hello World!");
    }
  }

  public interface J extends I {}

  public static class A implements I {}

  public static class B implements J {}

  public static class Main {

    public static void main(String[] args) throws Exception {
      Object o = Class.forName(args[0]).getDeclaredConstructor().newInstance();
      o.getClass().getMethod(args[1]).invoke(o);
    }
  }
}
