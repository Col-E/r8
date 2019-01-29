// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;

public class InliningAfterClassInitializationTest extends TestBase {

  @Test
  public void testClass1() throws Exception {
    Class<TestClass1> mainClass = TestClass1.class;
    CodeInspector inspector =
        buildAndRun(
            mainClass,
            StringUtils.lines("In A.<clinit>()", "In A.notInlineable()", "In A.inlineable()"));

    ClassSubject classA = inspector.clazz(A.class);
    assertThat(classA, isPresent());

    MethodSubject inlineableMethod = classA.uniqueMethodWithName("inlineable");
    assertThat(inlineableMethod, not(isPresent()));

    MethodSubject notInlineableMethod = classA.uniqueMethodWithName("notInlineable");
    assertThat(notInlineableMethod, isPresent());

    MethodSubject testMethod = inspector.clazz(mainClass).mainMethod();
    assertThat(testMethod, isPresent());
    assertThat(testMethod, invokesMethod(notInlineableMethod));
  }

  @Test
  public void testClass2() throws Exception {
    Class<TestClass2> mainClass = TestClass2.class;
    CodeInspector inspector =
        buildAndRun(
            mainClass,
            StringUtils.lines("In A.<clinit>()", "Field A.staticField", "In A.inlineable()"));

    ClassSubject classA = inspector.clazz(A.class);
    assertThat(classA, isPresent());

    MethodSubject inlineableMethod = classA.uniqueMethodWithName("inlineable");
    assertThat(inlineableMethod, not(isPresent()));
  }

  @Test
  public void testClass3() throws Exception {
    Class<TestClass3> mainClass = TestClass3.class;
    CodeInspector inspector =
        buildAndRun(mainClass, StringUtils.lines("In A.<clinit>()", "In A.inlineable()"));

    ClassSubject classA = inspector.clazz(A.class);
    assertThat(classA, isPresent());

    MethodSubject inlineableMethod = classA.uniqueMethodWithName("inlineable");
    assertThat(inlineableMethod, not(isPresent()));
  }

  @Test
  public void testClass4() throws Exception {
    Class<TestClass4> mainClass = TestClass4.class;
    CodeInspector inspector =
        buildAndRun(
            mainClass,
            StringUtils.lines("In A.<clinit>()", "Field A.instanceField", "In A.inlineable()"));

    ClassSubject classA = inspector.clazz(A.class);
    assertThat(classA, isPresent());

    MethodSubject inlineableMethod = classA.uniqueMethodWithName("inlineable");
    assertThat(inlineableMethod, not(isPresent()));
  }

  @Test
  public void testClass5() throws Exception {
    Class<TestClass5> mainClass = TestClass5.class;
    CodeInspector inspector =
        buildAndRun(mainClass, StringUtils.lines("In A.<clinit>()", "In A.inlineable()"));

    ClassSubject classA = inspector.clazz(A.class);
    assertThat(classA, isPresent());

    MethodSubject inlineableMethod = classA.uniqueMethodWithName("inlineable");
    assertThat(inlineableMethod, not(isPresent()));
  }

  @Test
  public void testClass6() throws Exception {
    Class<TestClass6> mainClass = TestClass6.class;
    CodeInspector inspector =
        buildAndRun(mainClass, StringUtils.lines("In A.<clinit>()", "In A.notInlineable()"));

    ClassSubject classA = inspector.clazz(A.class);
    assertThat(classA, isPresent());

    MethodSubject notInlineableMethod = classA.uniqueMethodWithName("notInlineable");
    assertThat(notInlineableMethod, isPresent());

    MethodSubject testMethod = inspector.clazz(mainClass).uniqueMethodWithName("test");
    assertThat(testMethod, isPresent());
    assertThat(testMethod, invokesMethod(notInlineableMethod));
  }

  @Test
  public void testClass7() throws Exception {
    Class<TestClass7> mainClass = TestClass7.class;
    CodeInspector inspector =
        buildAndRun(
            mainClass,
            StringUtils.lines(
                "Caught NullPointerException",
                "In A.<clinit>()",
                "Field A.instanceField",
                "In A.inlineable()"));

    ClassSubject classA = inspector.clazz(A.class);
    assertThat(classA, isPresent());

    MethodSubject inlineableMethod = classA.uniqueMethodWithName("inlineable");
    assertThat(inlineableMethod, not(isPresent()));
  }

  @Test
  public void testClass8() throws Exception {
    Class<TestClass8> mainClass = TestClass8.class;
    CodeInspector inspector =
        buildAndRun(
            mainClass,
            StringUtils.lines(
                "In A.<clinit>()", "In A.notInlineable()", "In A.alsoNotInlineable()"));

    ClassSubject classA = inspector.clazz(A.class);
    assertThat(classA, isPresent());

    MethodSubject notInlineableMethod = classA.uniqueMethodWithName("notInlineable");
    assertThat(notInlineableMethod, isPresent());

    MethodSubject alsoNotInlineableMethod = classA.uniqueMethodWithName("alsoNotInlineable");
    assertThat(alsoNotInlineableMethod, isPresent());

    MethodSubject testMethod = inspector.clazz(mainClass).mainMethod();
    assertThat(testMethod, isPresent());
    assertThat(testMethod, invokesMethod(notInlineableMethod));
    assertThat(testMethod, invokesMethod(alsoNotInlineableMethod));
  }

  @Test
  public void testClass9() throws Exception {
    Class<TestClass9> mainClass = TestClass9.class;
    CodeInspector inspector =
        buildAndRun(
            mainClass,
            StringUtils.lines("In A.<clinit>()", "Field A.staticField", "In A.notInlineable()"));

    ClassSubject classA = inspector.clazz(A.class);
    assertThat(classA, isPresent());

    MethodSubject notInlineableMethod = classA.uniqueMethodWithName("notInlineable");
    assertThat(notInlineableMethod, isPresent());

    MethodSubject testMethod = inspector.clazz(mainClass).mainMethod();
    assertThat(testMethod, isPresent());
    assertThat(testMethod, invokesMethod(notInlineableMethod));
  }

  private CodeInspector buildAndRun(Class<?> mainClass, String expectedOutput) throws Exception {
    testForJvm().addTestClasspath().run(mainClass).assertSuccessWithOutput(expectedOutput);

    return testForR8(Backend.DEX)
        .addInnerClasses(InliningAfterClassInitializationTest.class)
        .addKeepMainRule(mainClass)
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .enableMergeAnnotations()
        .run(mainClass)
        .assertSuccessWithOutput(expectedOutput)
        .inspector();
  }

  static class TestClass1 {

    public static void main(String[] args) {
      A.notInlineable();

      // Since the previous call will cause the initializer of A to run, we can safely inline this
      // call.
      A.inlineable();
    }
  }

  static class TestClass2 {

    public static void main(String[] args) {
      System.out.println(A.staticField);

      // Since the previous instruction will cause the initializer of A to run, we can safely inline
      // this call.
      A.inlineable();
    }
  }

  static class TestClass3 {

    public static void main(String[] args) {
      A.staticField = "Hello world!";

      // Since the previous instruction will cause the initializer of A to run, we can safely inline
      // this call.
      A.inlineable();

      // Make sure the field is read to prevent the static-put instruction from being removed
      // (b/123553485).
      spuriousFieldRead();
    }

    private static void spuriousFieldRead() {
      if (System.currentTimeMillis() < 0) {
        System.out.println(A.staticField);
      }
    }
  }

  static class TestClass4 {

    public static void main(String[] args) {
      test(new A());
    }

    @NeverInline
    private static void test(A obj) {
      System.out.println(obj.instanceField);

      // Since the previous instruction will cause the initializer of A to run, we can safely inline
      // this call.
      A.inlineable();
    }
  }

  static class TestClass5 {

    public static void main(String[] args) {
      test(new A());
    }

    @NeverInline
    private static void test(A obj) {
      obj.instanceField = "Hello world!";

      // Since the previous instruction will cause the initializer of A to run, we can safely inline
      // this call.
      A.inlineable();

      // Make sure the field is read to prevent the instance-put instruction from being removed.
      spuriousFieldRead(obj);
    }

    private static void spuriousFieldRead(A obj) {
      if (System.currentTimeMillis() < 0) {
        System.out.println(obj.instanceField);
      }
    }
  }

  static class TestClass6 {

    public static void main(String[] args) {
      test(null);
    }

    @KeepConstantArguments
    @NeverInline
    private static void test(A obj) {
      try {
        String value = obj.instanceField;
        System.out.println(value);
      } catch (NullPointerException e) {
        // Ignore.
      }

      // This call cannot be inlined because `obj.field` may throw a NullPointerException, in which
      // case A is not guaranteed to be initialized.
      A.notInlineable();
    }
  }

  static class TestClass7 {

    public static void main(String[] args) {
      test(null);
      test(new A());
    }

    @NeverInline
    private static void test(A obj) {
      try {
        String value = obj.instanceField;
        System.out.println(value);
      } catch (NullPointerException e) {
        // Ignore.
        System.out.println("Caught NullPointerException");
        return;
      }

      // Due to the `return` in the catch handler above, A is guaranteed to be initialized if we
      // reach this line.
      A.inlineable();
    }
  }

  static class TestClass8 {

    public static void main(String[] args) {
      try {
        A.notInlineable();
      } catch (ExceptionInInitializerError e) {
        System.out.println("Caught ExceptionInInitializerError");
      }

      A.alsoNotInlineable();
    }
  }

  static class TestClass9 {

    public static void main(String[] args) {
      try {
        String value = A.staticField;
        System.out.println(value);
      } catch (ExceptionInInitializerError e) {
        System.out.println("Caught ExceptionInInitializerError");
      }

      A.notInlineable();
    }
  }

  static class A {

    public String instanceField = "Field A.instanceField";
    public static String staticField =
        System.currentTimeMillis() >= 0 ? "Field A.staticField" : null;

    static {
      System.out.println("In A.<clinit>()");
    }

    static void notInlineable() {
      System.out.println("In A.notInlineable()");
    }

    static void alsoNotInlineable() {
      System.out.println("In A.alsoNotInlineable()");
    }

    static void inlineable() {
      System.out.println("In A.inlineable()");
    }
  }
}
