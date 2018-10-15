// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.verticalclassmerging;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;

public class MergedFieldTypeTest extends MergedTypeBaseTest {

  static class TestClass {

    private static A field = new B();

    public static void main(String[] args) {
      System.out.print(field.getClass().getName());
    }
  }

  public MergedFieldTypeTest(Backend backend, boolean enableVerticalClassMerging) {
    super(backend, enableVerticalClassMerging);
  }

  @Override
  public Class<?> getTestClass() {
    return TestClass.class;
  }

  @Override
  public String getConditionForProguardIfRule() {
    return "-if class **$TestClass { **$A field; }";
  }

  @Override
  public String getExpectedStdout() {
    return B.class.getName();
  }

  public static class MergedFieldTypeWithCollisionTest extends MergedTypeBaseTest {

    static class SuperTestClass {

      private A field = new B();

      public A get() {
        return field;
      }
    }

    static class TestClass extends SuperTestClass {

      private A field = null;

      public static void main(String[] args) {
        TestClass obj = new TestClass();
        if (false) {
          obj.field = new B();
          System.out.println(obj.field);
        }
        System.out.print(obj.get().getClass().getName());
      }
    }

    public MergedFieldTypeWithCollisionTest(Backend backend, boolean enableVerticalClassMerging) {
      super(backend, enableVerticalClassMerging, ImmutableList.of(SuperTestClass.class));
    }

    @Override
    public Class<?> getTestClass() {
      return TestClass.class;
    }

    @Override
    public String getConditionForProguardIfRule() {
      return "-if class **$SuperTestClass { **$A field; }";
    }

    @Override
    public String getExpectedStdout() {
      return B.class.getName();
    }

    @Override
    public void inspect(CodeInspector inspector) {
      super.inspect(inspector);

      ClassSubject testClassSubject = inspector.clazz(TestClass.class);
      assertThat(testClassSubject, isPresent());

      if (enableVerticalClassMerging) {
        // Verify that SuperTestClass has been merged into TestClass.
        assertThat(inspector.clazz(SuperTestClass.class), not(isPresent()));
        assertEquals("java.lang.Object", testClassSubject.getDexClass().superType.toSourceString());

        // Verify that TestClass.field has been removed.
        assertEquals(1, testClassSubject.allFields().size());

        // Verify that there was a naming conflict such that SuperTestClass.field was renamed.
        assertNotEquals("field", testClassSubject.allFields().get(0).getFinalName());
      }
    }
  }
}
