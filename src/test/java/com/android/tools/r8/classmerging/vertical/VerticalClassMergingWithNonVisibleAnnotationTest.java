// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.classmerging.vertical.testclasses.VerticalClassMergingWithNonVisibleAnnotationTestClasses;
import com.android.tools.r8.classmerging.vertical.testclasses.VerticalClassMergingWithNonVisibleAnnotationTestClasses.Base;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.AnnotationSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergingWithNonVisibleAnnotationTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public VerticalClassMergingWithNonVisibleAnnotationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(VerticalClassMergingWithNonVisibleAnnotationTestClasses.class)
        .addProgramClasses(Sub.class)
        .setMinApi(parameters)
        .addKeepMainRule(Sub.class)
        .addKeepClassRules(
            VerticalClassMergingWithNonVisibleAnnotationTestClasses.class.getTypeName()
                + "$Private* { *; }")
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(Base.class))
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Sub.class)
        .assertSuccessWithOutputLines("Base::foo()", "Sub::bar()")
        .inspect(
            codeInspector -> {
              ClassSubject sub = codeInspector.clazz(Sub.class);
              assertThat(sub, isPresent());
              // Assert that the merged class has no annotations from Base
              assertTrue(sub.getDexProgramClass().annotations().isEmpty());
              // Assert that foo has the private annotation from the Base.foo
              MethodSubject foo =
                  sub.uniqueMethodThatMatches(
                      method ->
                          method.getOriginalName(false).equals("foo") && !method.isSynthetic());
              assertThat(foo, isPresent());
              AnnotationSubject privateMethodAnnotation =
                  foo.annotation(
                      VerticalClassMergingWithNonVisibleAnnotationTestClasses.class.getTypeName()
                          + "$PrivateMethodAnnotation");
              assertThat(privateMethodAnnotation, isPresent());
            });
  }

  @NeverClassInline
  public static class Sub extends Base {

    @Override
    @NeverInline
    public void bar() {
      System.out.println("Sub::bar()");
    }

    public static void main(String[] args) {
      Sub sub = new Sub();
      sub.foo();
      sub.bar();
    }
  }
}
