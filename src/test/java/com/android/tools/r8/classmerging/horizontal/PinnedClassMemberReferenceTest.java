// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;

public class PinnedClassMemberReferenceTest extends HorizontalClassMergingTestBase {
  public PinnedClassMemberReferenceTest(TestParameters parameters) {
    super(parameters);
  }

  private R8FullTestBuilder testCommon() throws Exception {
    return testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addDontObfuscate()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters);
  }

  private R8TestRunResult runAndAssertOutput(R8FullTestBuilder builder) throws Exception {
    return builder
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "a", "b", "foo a: bar", "foo b: baz", "fields a: bar", "fields b: baz");
  }

  @Test
  public void testWithoutKeepRules() throws Exception {
    // This is just a small check ensure that without the keep rules the classes are merged.
    assumeTrue(parameters.isCfRuntime());

    runAndAssertOutput(testCommon())
        .inspect(
            codeInspector -> {
              ClassSubject aClassSubject = codeInspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              assertThat(codeInspector.clazz(B.class), not(isPresent()));

              ClassSubject cClassSubject = codeInspector.clazz(C.class);
              assertThat(cClassSubject, isPresent());
              assertThat(cClassSubject.field(aClassSubject.getFinalName(), "a"), isPresent());
              assertThat(cClassSubject.field(aClassSubject.getFinalName(), "b"), isPresent());

              assertThat(
                  cClassSubject.method("void", "foo", aClassSubject.getFinalName()), isPresent());
            });
  }

  @Test
  public void testWithKeepRules() throws Exception {
    runAndAssertOutput(
            testCommon()
                .addKeepRules(
                    "-keepclassmembers class " + C.class.getTypeName() + " { ",
                    "  " + A.class.getTypeName() + " a;",
                    "  " + C.class.getTypeName() + " c;",
                    "  void foo(" + A.class.getTypeName() + ");",
                    "  void foo(" + B.class.getTypeName() + ");",
                    "}"))
        .inspect(
            codeInspector -> {
              ClassSubject aClassSubject = codeInspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              ClassSubject bClassSubject = codeInspector.clazz(B.class);
              assertThat(bClassSubject, isPresent());

              ClassSubject cClassSubject = codeInspector.clazz(C.class);
              assertThat(cClassSubject, isPresent());
              assertThat(cClassSubject.field(aClassSubject.getFinalName(), "a"), isPresent());
              assertThat(cClassSubject.field(bClassSubject.getFinalName(), "b"), isPresent());

              assertThat(
                  cClassSubject.method("void", "foo", aClassSubject.getFinalName()), isPresent());
              assertThat(
                  cClassSubject.method("void", "foo", bClassSubject.getFinalName()), isPresent());
            });
  }

  @NeverClassInline
  public static class A {
    public A() {
      System.out.println("a");
    }

    @NeverInline
    public String bar() {
      return "bar";
    }
  }

  @NeverClassInline
  public static class B {
    public B() {
      System.out.println("b");
    }

    @NeverInline
    public String baz() {
      return "baz";
    }
  }

  @NeverClassInline
  public static class C {
    A a;
    B b;

    public C(A a, B b) {
      this.a = a;
      this.b = b;
    }

    @NeverInline
    public void foo(A a2) {
      System.out.println("foo a: " + a2.bar());
    }

    @NeverInline
    public void foo(B b) {
      System.out.println("foo b: " + b.baz());
    }

    @NeverInline
    public void fields() {
      System.out.println("fields a: " + a.bar());
      System.out.println("fields b: " + b.baz());
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A();
      B b = new B();
      C c = new C(a, b);
      c.foo(a);
      c.foo(b);
      c.fields();
    }
  }
}
