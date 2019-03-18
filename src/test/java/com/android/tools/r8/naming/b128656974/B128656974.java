// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.b128656974;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.naming.testclasses.Greeting;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Ignore;
import org.junit.Test;

public class B128656974 extends TestBase {

  @Ignore("b/128656974")
  @Test
  public void testField() throws Exception {
    Class<?> main = TestClassMainForField.class;
    testForR8(Backend.DEX)
        .addProgramClasses(
            Greeting.class, Greeting.getGreetingBase(), TestClassSub.class, main)
        .enableClassInliningAnnotations()
        .enableInliningAnnotations()
        .enableMergeAnnotations()
        .addKeepMainRule(main)
        .addKeepRules(
            "-keepclassmembernames class "
                + TestClassSub.class.getTypeName()
                + "{ static java.lang.String a; }")
        .run(main)
        .assertSuccessWithOutput(StringUtils.lines("TestClassSub.greeting", "TestClassSub.a"))
        .inspect(inspector -> {
          ClassSubject greetingBase = inspector.clazz(Greeting.getGreetingBase());
          assertThat(greetingBase, isPresent());
          FieldSubject greeting = greetingBase.uniqueFieldWithName("greeting");
          assertThat(greeting, isPresent());
          assertThat(greeting, isRenamed());
          assertNotEquals("a", greeting.getFinalName());
        });
  }

  @NeverClassInline
  static class TestClassSub extends Greeting {
    // Since this name is kept, renaming of Greeting.greeting should avoid `a`.
    // Otherwise, we'll see Out-of-order field_ids due to the duplicate fields.
    static String a;

    TestClassSub() {
      greeting = "TestClassSub.greeting";
      a = "TestClassSub.a";
    }

    @Override
    public String toString(){
      return greeting + System.lineSeparator() + a;
    }
  }

  static class TestClassMainForField {
    public static void main(String[] args) {
      TestClassSub instance = new TestClassSub();
      System.out.println(instance.toString());
    }
  }

  @Test
  public void testMethod() throws Exception {
    Class<?> main = TestClassMainForMethod.class;
    testForR8(Backend.DEX)
        .addProgramClasses(
            TestClassBase.class, TestClassSub2.class, main)
        .enableMergeAnnotations()
        .enableClassInliningAnnotations()
        .enableInliningAnnotations()
        .addKeepMainRule(main)
        .addKeepRules(
            "-keepclassmembernames class "
                + TestClassSub2.class.getTypeName()
                + "{ void a(...); }")
        .run(main)
        .assertSuccessWithOutput(StringUtils.lines("TestClassSub2::a", "TestClassBase::foo"))
        .inspect(inspector -> {
          ClassSubject base = inspector.clazz(TestClassBase.class);
          assertThat(base, isPresent());
          MethodSubject foo = base.uniqueMethodWithName("foo");
          assertThat(foo, isPresent());
          assertThat(foo, isRenamed());
          assertNotEquals("a", foo.getFinalName());
        });
  }

  @NeverMerge
  static class TestClassBase {
    @NeverInline
    void foo() {
      System.out.println("TestClassBase::foo");
    }
  }

  @NeverClassInline
  static class TestClassSub2 extends TestClassBase {
    // Since this name is kept, renaming of TestClassBase#foo should avoid `a`.
    // Otherwise, we'll see Out-of-order method_ids due to the duplicate methods.
    @NeverInline
    void a() {
      System.out.println("TestClassSub2::a");
    }
  }

  static class TestClassMainForMethod {
    public static void main(String[] args) {
      TestClassSub2 instance = new TestClassSub2();
      instance.a();
      instance.foo();
    }
  }

}
