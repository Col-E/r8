// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.uninstantiatedtypes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceMethodTest extends TestBase {

  private final Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public InterfaceMethodTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("In A.m()", "In B.m()");

    if (backend == Backend.CF) {
      testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expectedOutput);
    }

    CodeInspector inspector =
        testForR8(backend)
            .addInnerClasses(InterfaceMethodTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .enableMergeAnnotations()
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    ClassSubject interfaceSubject = inspector.clazz(I.class);
    assertThat(interfaceSubject, isPresent());
    assertThat(interfaceSubject.method(Uninstantiated.class.getTypeName(), "m"), isPresent());

    for (Class<?> clazz : ImmutableList.of(A.class, B.class)) {
      ClassSubject classSubject = inspector.clazz(clazz);
      assertThat(classSubject, isPresent());
      assertThat(classSubject.method(Uninstantiated.class.getTypeName(), "m"), isPresent());
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      test(new A());
      test(new B());
    }

    @NeverInline
    private static void test(I obj) {
      obj.m();
    }
  }

  @NeverMerge
  interface I {

    Uninstantiated m();
  }

  static class A implements I {

    @NeverInline
    @Override
    public Uninstantiated m() {
      System.out.println("In A.m()");
      return null;
    }
  }

  // The purpose of this class is merely to avoid that the invoke-interface instruction in
  // TestClass.test() gets devirtualized to an invoke-virtual instruction. Otherwise the method
  // I.m() would not be present in the output.
  static class B implements I {

    @Override
    public Uninstantiated m() {
      System.out.println("In B.m()");
      return null;
    }
  }

  static class Uninstantiated {}
}
