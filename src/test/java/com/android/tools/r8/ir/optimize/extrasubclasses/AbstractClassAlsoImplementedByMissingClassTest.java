// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.extrasubclasses;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AbstractClassAlsoImplementedByMissingClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public AbstractClassAlsoImplementedByMissingClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  /**
   * Tests that it is possible to provide an implementation of an abstract class after the program
   * has been compiled with R8, as long as the abstract class and its methods have been kept.
   */
  @Test
  public void test() throws Exception {
    Path r8Out =
        testForR8(parameters.getBackend())
            // C is not visible to the R8 compilation.
            .addProgramClasses(TestClass.class, A.class, B.class)
            // Helper is added on the classpath such that R8 doesn't know what it does.
            .addClasspathClasses(Helper.class)
            .addKeepMainRule(TestClass.class)
            // Keeping A, A.<init>(), and A.kept() should make it possible to provide an
            // implementation
            // of A after the R8 compilation.
            .addKeepRules(
                "-keep class " + A.class.getTypeName() + " { void <init>(); void kept(); }")
            .enableNeverClassInliningAnnotations()
            .setMinApi(parameters)
            .compile()
            .inspect(this::inspect)
            .writeToZip();

    testForRuntime(parameters)
        .addProgramClasses(C.class, Helper.class)
        .addRunClasspathFiles(r8Out)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!", "The end");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject.uniqueMethodWithOriginalName("kept"), isPresent());

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject.uniqueMethodWithOriginalName("kept"), isPresent());

    // A.notKept() and B.notKept() should not be present, because the only invoke instruction
    // targeting A.notKept() should have been inlined.
    assertThat(aClassSubject.uniqueMethodWithOriginalName("notKept"), not(isPresent()));
    assertThat(bClassSubject.uniqueMethodWithOriginalName("notKept"), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      // Casting `notAlwaysB` to B and invoking B.kept() would lead to a ClassCastException.
      A notAlwaysB = System.currentTimeMillis() >= 0 ? Helper.getInstance() : new B();
      notAlwaysB.kept();

      // We should be able to inline A.notKept() when the receiver is guaranteed to be B.
      A alwaysB = new B();
      alwaysB.notKept();

      System.out.println("The end");
    }
  }

  abstract static class A {

    abstract void kept();

    abstract void notKept();
  }

  @NeverClassInline
  static class B extends A {

    @Override
    public void kept() {
      throw new RuntimeException();
    }

    @Override
    public void notKept() {
      System.out.println(" world!");
    }
  }

  // Only declarations are visible via the classpath.
  static class Helper {

    static A getInstance() {
      return new C();
    }
  }

  // Not visible during the R8 compilation.
  static class C extends A {

    @Override
    public void kept() {
      System.out.print("Hello");
    }

    @Override
    public void notKept() {
      throw new RuntimeException();
    }
  }
}
