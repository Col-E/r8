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
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Collectors;

public class MergedReturnTypeTest extends MergedTypeBaseTest {

  static class TestClass {

    public static void main(String[] args) {
      System.out.print(method().getClass().getName());
    }

    public static A method() {
      return new B();
    }
  }

  public MergedReturnTypeTest(Backend backend, boolean enableVerticalClassMerging) {
    super(backend, enableVerticalClassMerging);
  }

  @Override
  public Class<?> getTestClass() {
    return TestClass.class;
  }

  @Override
  public String getConditionForProguardIfRule() {
    return "-if class **$TestClass { **$A method(); }";
  }

  @Override
  public String getExpectedStdout() {
    return B.class.getName();
  }

  public static class MergedReturnTypeWithCollisionTest extends MergedTypeBaseTest {

    static class SuperTestClass {

      public static A method() {
        return new B();
      }
    }

    static class TestClass extends SuperTestClass {

      public static void main(String[] args) {
        B obj = new B();
        if (obj == null) {
          System.out.print(TestClass.method().getClass().getName());
        }
        System.out.print(SuperTestClass.method().getClass().getName());
      }

      public static A method() {
        return new B();
      }
    }

    public MergedReturnTypeWithCollisionTest(Backend backend, boolean enableVerticalClassMerging) {
      super(backend, enableVerticalClassMerging, ImmutableList.of(SuperTestClass.class));
    }

    @Override
    public Class<?> getTestClass() {
      return TestClass.class;
    }

    @Override
    public String getConditionForProguardIfRule() {
      return "-if class **$SuperTestClass { **$A method(); }";
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

        // Verify that TestClass.method has been removed.
        List<FoundMethodSubject> methods =
            testClassSubject.allMethods().stream()
                .filter(subject -> subject.getFinalName().contains("method"))
                .collect(Collectors.toList());
        assertEquals(1, methods.size());

        // Verify that there was a naming conflict such that SuperTestClass.method was renamed.
        assertNotEquals("method", methods.get(0).getFinalName());
      }
    }
  }
}
