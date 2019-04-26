// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.hamcrest.core.AnyOf;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * MethodNameMinificationPrivateNamedLastTest tests that private methods are named after public
 * methods. Public virtual methods may be overridden and used in the sub-class hierarchy and
 * therefore it is preferable to have their names as small as possible.
 */
@RunWith(Parameterized.class)
public class MethodNameMinificationPrivateNamedLastTest extends TestBase {

  public static final String EXPECTED_OUTPUT =
      StringUtils.lines("A.m1", "A.m2", "B.m1", "B.m2", "B.m3", "C.m1", "C.m2", "C.m3", "C.m4");

  @NeverMerge
  public static class A {

    @NeverInline
    public void m1() {
      System.out.println("A.m1");
      m2();
    }

    @NeverInline
    private void m2() {
      System.out.println("A.m2");
    }
  }

  @NeverMerge
  public static class B extends A {

    @NeverInline
    public void m1() {
      System.out.println("B.m1");
      m2();
    }

    @NeverInline
    private void m2() {
      System.out.println("B.m2");
    }

    @NeverInline
    public void m3() {
      System.out.println("B.m3");
    }
  }

  @NeverMerge
  public static class C extends B {

    @NeverInline
    public void m1() {
      System.out.println("C.m1");
      m2();
    }

    @NeverInline
    private void m2() {
      System.out.println("C.m2");
    }

    @NeverInline
    public void m3() {
      System.out.println("C.m3");
      m4();
    }

    @NeverInline
    private void m4() {
      System.out.println("C.m4");
    }
  }

  public static class Runner {

    public static void main(String[] args) {
      A a = new A();
      a.m1();
      B b = new B();
      b.m1();
      b.m3();
      C c = new C();
      c.m1();
      c.m3();
    }
  }

  private TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public MethodNameMinificationPrivateNamedLastTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testInheritedNamingState()
      throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addInnerClasses(MethodNameMinificationPrivateNamedLastTest.class)
        .enableMergeAnnotations()
        .enableInliningAnnotations()
        .enableClassInliningAnnotations()
        .addKeepMainRule(Runner.class)
        .setMinApi(parameters.getRuntime())
        .compile()
        .run(parameters.getRuntime(), Runner.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector -> {
              assertEquals("a", inspector.clazz(A.class).uniqueMethodWithName("m1").getFinalName());
              assertEquals("b", inspector.clazz(A.class).uniqueMethodWithName("m2").getFinalName());
              assertEquals("a", inspector.clazz(B.class).uniqueMethodWithName("m1").getFinalName());
              assertEquals("c", inspector.clazz(B.class).uniqueMethodWithName("m2").getFinalName());
              assertEquals("b", inspector.clazz(B.class).uniqueMethodWithName("m3").getFinalName());
              ClassSubject cSubject = inspector.clazz(C.class);
              assertEquals("a", cSubject.uniqueMethodWithName("m1").getFinalName());
              assertEquals("b", cSubject.uniqueMethodWithName("m3").getFinalName());
              AnyOf<String> cPrivateNamesP = anyOf(is("c"), is("d"));
              assertThat(cSubject.uniqueMethodWithName("m2").getFinalName(), cPrivateNamesP);
              // TODO(mkroghj) This is not working currently. See if it gets fixed by removing the
              //  extra pass for private methods.
              // assertThat(cSubject.uniqueMethodWithName("m4").getFinalName(), cPrivateNamesP);
            });
  }
}
