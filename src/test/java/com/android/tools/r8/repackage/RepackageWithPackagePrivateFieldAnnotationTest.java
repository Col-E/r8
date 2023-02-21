// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageWithPackagePrivateFieldAnnotationTest extends RepackageTestBase {

  public RepackageWithPackagePrivateFieldAnnotationTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void test() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepClassRules(NonPublicKeptAnnotation.class)
        .addKeepRuntimeVisibleAnnotations()
        .apply(this::configureRepackaging)
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(IneligibleForRepackaging.class);
    assertThat(classSubject, isPresent());

    // Verify that the class was not repackaged.
    assertEquals(
        IneligibleForRepackaging.class.getPackage().getName(),
        classSubject.getDexProgramClass().getType().getPackageName());
  }

  public static class TestClass {

    public static void main(String[] args) {
      IneligibleForRepackaging.greet();
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD})
  @interface NonPublicKeptAnnotation {}

  public static class IneligibleForRepackaging {

    @NeverPropagateValue @NonPublicKeptAnnotation private static String GREETING = "Hello world!";

    @NeverInline
    public static void greet() {
      System.out.println(GREETING);
    }
  }
}
