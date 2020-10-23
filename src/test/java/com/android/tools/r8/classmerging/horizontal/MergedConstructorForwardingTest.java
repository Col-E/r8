// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.readsInstanceField;
import static com.android.tools.r8.utils.codeinspector.Matchers.writesInstanceField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.horizontalclassmerging.ClassMerger;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;

public class MergedConstructorForwardingTest extends HorizontalClassMergingTestBase {

  public MergedConstructorForwardingTest(
      TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42", "13", "21", "39", "print a", "print b")
        .inspect(
            codeInspector -> {
              if (enableHorizontalClassMerging) {
                ClassSubject aClassSubject = codeInspector.clazz(A.class);
                assertThat(aClassSubject, isPresent());
                FieldSubject classIdFieldSubject =
                    aClassSubject.uniqueFieldWithName(ClassMerger.CLASS_ID_FIELD_NAME);
                assertThat(classIdFieldSubject, isPresent());

                MethodSubject firstInitSubject = aClassSubject.init("int");
                assertThat(firstInitSubject, isPresent());
                assertThat(
                    firstInitSubject, writesInstanceField(classIdFieldSubject.getDexField()));

                MethodSubject otherInitSubject = aClassSubject.init("long", "int");
                assertThat(otherInitSubject, isPresent());
                assertThat(
                    otherInitSubject, writesInstanceField(classIdFieldSubject.getDexField()));

                MethodSubject printSubject = aClassSubject.method("void", "print$bridge");
                assertThat(printSubject, isPresent());
                assertThat(printSubject, readsInstanceField(classIdFieldSubject.getDexField()));

                assertThat(codeInspector.clazz(B.class), not(isPresent()));

                // TODO(b/165517236): Explicitly check classes have been merged.
              } else {
                assertThat(codeInspector.clazz(A.class), isPresent());
                assertThat(codeInspector.clazz(B.class), isPresent());
              }
            });
  }

  @NeverClassInline
  public static class A {
    public A() {
      this(42);
    }

    public A(long x) {
      System.out.println(x);
    }

    @NeverInline
    public void print() {
      System.out.println("print a");
    }
  }

  @NeverClassInline
  public static class B {
    public B() {
      this(7);
    }

    public B(long y) {
      System.out.println(y * 3);
    }

    @NeverInline
    public void print() {
      System.out.println("print b");
    }
  }

  public static class Main {
    public static void main(String[] args) {
      A a = new A();
      a = new A(13);
      B b = new B();
      b = new B(13);
      a.print();
      b.print();
    }
  }
}
