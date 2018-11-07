// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.instanceofremoval;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InstanceOfRemovalTest extends TestBase {

  static class A {}

  static class B extends A {}

  static class TestClass {

    public static void main(String[] args) {
      foo();
      bar();
    }

    @NeverInline
    private static void foo() {
      System.out.println("A instanceof A: " + (getAForFoo() instanceof A));
      System.out.println("A instanceof B: " + (getAForFoo() instanceof B));
      System.out.println("B instanceof A: " + (getBForFoo() instanceof A));
      System.out.println("B instanceof B: " + (getBForFoo() instanceof B));
      System.out.println("null instanceof A: " + (getNullForFoo() instanceof A));
      System.out.println("null instanceof B: " + (getNullForFoo() instanceof B));
    }

    public static A getAForFoo() {
      return new A();
    }

    public static A getBForFoo() {
      return new B();
    }

    public static A getNullForFoo() { return null; }

    @NeverInline
    private static void bar() {
      System.out.println("A instanceof A: " + (getAForBar() instanceof A));
      System.out.println("A instanceof B: " + (getAForBar() instanceof B));
      System.out.println("B instanceof A: " + (getBForBar() instanceof A));
      System.out.println("B instanceof B: " + (getBForBar() instanceof B));
      System.out.println("null instanceof A: " + (getNullForBar(true) instanceof A));
      System.out.print("null instanceof B: " + (getNullForBar(true) instanceof B));
    }

    @NeverInline
    public static A getAForBar() {
      return new A();
    }

    @NeverInline
    public static A getBForBar() {
      return new B();
    }

    @NeverInline
    public static A getNullForBar(boolean returnNull) {
      // Avoid `return null` since we will then use the fact that getNullForBar() always returns
      // null at the call site.
      return returnNull ? null : new A();
    }
  }

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  private final Backend backend;

  public InstanceOfRemovalTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    String expected =
        StringUtils.joinLines(
            "A instanceof A: true",
            "A instanceof B: false",
            "B instanceof A: true",
            "B instanceof B: true",
            "null instanceof A: false",
            "null instanceof B: false",
            "A instanceof A: true",
            "A instanceof B: false",
            "B instanceof A: true",
            "B instanceof B: true",
            "null instanceof A: false",
            "null instanceof B: false");

    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expected);

    CodeInspector inspector =
        testForR8(backend)
            .addProgramClasses(A.class, B.class, TestClass.class)
            .addKeepMainRule(TestClass.class)
            .addKeepAllClassesRule()
            .enableInliningAnnotations()
            .run(TestClass.class)
            .assertSuccessWithOutput(expected)
            .inspector();

    // With inlining we can prove that all instance-of checks succeed or fail.
    MethodSubject fooMethodSubject =
        inspector.clazz(TestClass.class).method("void", "foo", ImmutableList.of());
    Iterator<InstructionSubject> fooInstructionIterator =
        fooMethodSubject.iterateInstructions(InstructionSubject::isInstanceOf);
    // TODO(b/119194613): Removal fails on some null constants. Change back to 0 once fixed.
    assertEquals(2, Streams.stream(fooInstructionIterator).count());

    // Without inlining we cannot prove any of the instance-of checks to be trivial.
    MethodSubject barMethodSubject =
        inspector.clazz(TestClass.class).method("void", "bar", ImmutableList.of());
    Iterator<InstructionSubject> barInstructionIterator =
        barMethodSubject.iterateInstructions(InstructionSubject::isInstanceOf);
    assertEquals(6, Streams.stream(barInstructionIterator).count());
  }
}
