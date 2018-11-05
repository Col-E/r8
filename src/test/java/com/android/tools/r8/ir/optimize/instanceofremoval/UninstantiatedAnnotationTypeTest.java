// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.instanceofremoval;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.Streams;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UninstantiatedAnnotationTypeTest extends TestBase {

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface CustomAnnotation {}

  @CustomAnnotation
  static class TestClass {

    public static void main(String[] args) {
      Annotation obj = TestClass.class.getAnnotations()[0];

      // Although CustomAnnotation is never instantiated directly via a new-instance instruction,
      // we cannot rewrite the following instance-of instruction to false.
      if (obj instanceof CustomAnnotation) {
        live();
      } else {
        dead();
      }
    }

    @NeverInline
    private static void live() {
      System.out.print("In TestClass.live()");
    }

    @NeverInline
    private static void dead() {
      System.out.print("In TestClass.dead()");
    }
  }

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  private final Backend backend;

  public UninstantiatedAnnotationTypeTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    String expected = "In TestClass.live()";

    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expected);

    CodeInspector inspector =
        testForR8(backend)
            .addInnerClasses(UninstantiatedAnnotationTypeTest.class)
            .addKeepMainRule(TestClass.class)
            .addKeepRules("-keepattributes RuntimeVisibleAnnotations")
            .enableInliningAnnotations()
            .run(TestClass.class)
            .assertSuccessWithOutput(expected)
            .inspector();

    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    assertThat(classSubject.method("void", "live"), isPresent());
    assertThat(classSubject.method("void", "dead"), isPresent());

    long numberOfInstanceOfInstructions =
        Streams.stream(
                classSubject.mainMethod().iterateInstructions(InstructionSubject::isInstanceOf))
            .count();
    assertEquals(1, numberOfInstanceOfInstructions);
  }
}
