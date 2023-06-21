// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.verticalclassmerging;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepIfPresentRuleWithVerticalClassMergingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public KeepIfPresentRuleWithVerticalClassMergingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(KeepIfPresentRuleWithVerticalClassMergingTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-if class * extends " + A.class.getTypeName(), "-keep class <1> { <init>(...); }")
        .enableInliningAnnotations()
        .enableNoAccessModificationAnnotationsForClasses()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject classASubject = inspector.clazz(A.class);
              assertThat(classASubject, not(isPresent()));

              ClassSubject classBSubject = inspector.clazz(B.class);
              assertThat(classBSubject, isPresent());
              assertThat(classBSubject.init(), isPresent());
              assertThat(classBSubject.uniqueMethodWithOriginalName("greet"), isPresent());
              assertEquals(2, classBSubject.allMethods().size());
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class TestClass {

    public static void main(String[] args) {
      B.greet();
    }
  }

  // TODO(b/287891322): Allow vertical class merging even when A is made public.
  @NoAccessModification
  static class A {}

  static class B extends A {

    @NeverInline
    static void greet() {
      System.out.println("Hello world!");
    }
  }
}
