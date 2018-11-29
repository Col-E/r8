// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.annotations;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AnnotationsOnTargetedMethodTest extends TestBase {

  private static final String expectedOutput =
      StringUtils.lines(
          "In InterfaceImpl.targetedMethod()",
          "In OtherInterfaceImpl.targetedMethod()",
          MyAnnotation.class.getName());

  private final Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public AnnotationsOnTargetedMethodTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void jvmTest() throws Exception {
    assumeTrue(
        "JVM test independent of Art version - only run when testing on latest",
        ToolHelper.getDexVm().getVersion().isLatest());
    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expectedOutput);
  }

  @Test
  public void r8Test() throws Exception {
    testForR8(backend)
        .addInnerClasses(AnnotationsOnTargetedMethodTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-keepattributes *Annotation*", "-dontobfuscate")
        .enableInliningAnnotations()
        .enableMergeAnnotations()
        .run(TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  static class TestClass {

    public static void main(String[] args) {
      test(new InterfaceImpl());
      test(new OtherInterfaceImpl());

      Method method = Interface.class.getDeclaredMethods()[0];
      for (Annotation annotation : method.getAnnotations()) {
        visitAnnotation((MyAnnotation) annotation);
      }
    }

    @NeverInline
    private static void test(Interface obj) {
      obj.targetedMethod();
    }

    @NeverInline
    private static void visitAnnotation(MyAnnotation annotation) {
      System.out.println(annotation.annotationType().getName());
    }
  }

  @NeverMerge
  interface Interface {

    @NeverInline
    @MyAnnotation
    void targetedMethod();
  }

  static class InterfaceImpl implements Interface {

    @NeverInline
    @Override
    public void targetedMethod() {
      System.out.println("In InterfaceImpl.targetedMethod()");
    }
  }

  static class OtherInterfaceImpl implements Interface {

    @NeverInline
    @Override
    public void targetedMethod() {
      System.out.println("In OtherInterfaceImpl.targetedMethod()");
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD})
  @interface MyAnnotation {}
}
