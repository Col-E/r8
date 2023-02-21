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
public class InterfaceAlsoImplementedByMissingClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InterfaceAlsoImplementedByMissingClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  /**
   * Tests that it is possible to provide an implementation of an interface after the program has
   * been compiled with R8, as long as the interface and its methods have been kept.
   */
  @Test
  public void test() throws Exception {
    Path r8Out =
        testForR8(parameters.getBackend())
            // B is not visible to the R8 compilation.
            .addProgramClasses(TestClass.class, I.class, A.class)
            // Helper is added on the classpath such that R8 doesn't know what it does.
            .addClasspathClasses(Helper.class)
            .addKeepMainRule(TestClass.class)
            // Keeping I and I.kept() should make it possible to provide an implementation of
            // I after the R8 compilation.
            .addKeepRules("-keep class " + I.class.getTypeName() + " { void kept(); }")
            .enableNeverClassInliningAnnotations()
            .setMinApi(parameters)
            .compile()
            .inspect(this::inspect)
            .writeToZip();

    testForRuntime(parameters)
        .addProgramClasses(B.class, Helper.class)
        .addRunClasspathFiles(r8Out)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!", "The end");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject iClassSubject = inspector.clazz(I.class);
    assertThat(iClassSubject.uniqueMethodWithOriginalName("kept"), isPresent());

    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject.uniqueMethodWithOriginalName("kept"), isPresent());

    // I.notKept() and A.notKept() should not be present, because the only invoke instruction
    // targeting I.notKept() should have been inlined.
    assertThat(iClassSubject.uniqueMethodWithOriginalName("notKept"), not(isPresent()));
    assertThat(aClassSubject.uniqueMethodWithOriginalName("notKept"), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      // Casting `notAlwaysA` to A and invoking A.kept() would lead to a ClassCastException.
      I notAlwaysA = System.currentTimeMillis() >= 0 ? Helper.getInstance() : new A();
      notAlwaysA.kept();

      // We should be able to inline I.notKept() when the receiver is guaranteed to be A.
      I alwaysA = new A();
      alwaysA.notKept();

      System.out.println("The end");
    }
  }

  interface I {

    void kept();

    void notKept();
  }

  @NeverClassInline
  static class A implements I {

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

    static I getInstance() {
      return new B();
    }
  }

  // Not visible during the R8 compilation.
  static class B implements I {

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
