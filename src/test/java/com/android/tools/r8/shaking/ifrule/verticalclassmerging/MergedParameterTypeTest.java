// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.verticalclassmerging;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Collectors;

public class MergedParameterTypeTest extends MergedTypeBaseTest {

  static class TestClass {

    public static void main(String[] args) {
      method(new B());
    }

    public static void method(A obj) {
      System.out.print(obj.getClass().getName());
    }
  }

  public MergedParameterTypeTest(TestParameters parameters, boolean enableVerticalClassMerging) {
    super(parameters, enableVerticalClassMerging);
  }

  @Override
  public Class<?> getTestClass() {
    return TestClass.class;
  }

  @Override
  public String getConditionForProguardIfRule() {
    return "-if class **$TestClass { void method(**$A); }";
  }

  @Override
  public String getExpectedStdout() {
    return B.class.getName();
  }

  public static class MergedParameterTypeWithCollisionTest extends MergedTypeBaseTest {

    @NoAccessModification
    @NoHorizontalClassMerging
    static class SuperTestClass {

      public static void method(A obj) {
        System.out.print(obj.getClass().getName());
      }
    }

    static class TestClass extends SuperTestClass {

      public static void main(String[] args) {
        B obj = new B();
        if (obj == null) {
          TestClass.method(obj);
        }
        SuperTestClass.method(obj);
      }

      public static void method(A obj) {
        System.out.print(obj.getClass().getName());
      }
    }

    public MergedParameterTypeWithCollisionTest(
        TestParameters parameters, boolean enableVerticalClassMerging) {
      super(parameters, enableVerticalClassMerging, ImmutableList.of(SuperTestClass.class));
    }

    @Override
    public void configure(R8FullTestBuilder builder) {
      super.configure(builder);
      builder.enableNoHorizontalClassMergingAnnotations();
    }

    @Override
    public Class<?> getTestClass() {
      return TestClass.class;
    }

    @Override
    public String getConditionForProguardIfRule() {
      return "-if class **$SuperTestClass { void method(**$A); }";
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
        assertEquals(
            "java.lang.Object", testClassSubject.getDexProgramClass().superType.toSourceString());

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
