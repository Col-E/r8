// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.utils.codeinspector.AnnotationSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;

public class ReferencedInAnnotationTest extends HorizontalClassMergingTestBase {

  public ReferencedInAnnotationTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(Annotation.class)
        .addKeepRuntimeVisibleAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!")
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    // TestClass and A should still be present.
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    // B should have been merged into A if horizontal class merging is enabled.
    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isAbsent());

    // The annotation on TestClass should now refer to A instead of B.
    AnnotationSubject annotationSubject =
        testClassSubject.annotation(Annotation.class.getTypeName());
    assertThat(annotationSubject, isPresent());

    DexEncodedAnnotation encodedAnnotation = annotationSubject.getAnnotation();
    assertEquals(1, encodedAnnotation.getNumberOfElements());

    DexValue annotationElementValue = encodedAnnotation.getElement(0).getValue();
    assertTrue(annotationElementValue.isDexValueType());

    DexType annotationElementValueType = annotationElementValue.asDexValueType().getValue();
    assertEquals(aClassSubject.getDexProgramClass().getType(), annotationElementValueType);
  }

  @Annotation(B.class)
  public static class TestClass {

    public static void main(String[] args) {
      new A();
      new B("");
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  @interface Annotation {
    Class<?> value();
  }

  @NeverClassInline
  public static class A {

    public A() {
      System.out.print("Hello");
    }
  }

  @NeverClassInline
  public static class B {

    public B(String s) {
      System.out.println(" world!");
    }
  }
}
