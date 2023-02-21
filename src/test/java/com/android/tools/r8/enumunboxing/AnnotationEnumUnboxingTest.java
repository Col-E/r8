// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AnnotationEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public AnnotationEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Assume.assumeFalse(
        "The methods values and valueOf are required for reflection.",
        enumKeepRules.toString().equals("none"));
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(AnnotationEnumUnboxingTest.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addKeepClassRules(ClassAnnotationDefault.class)
        .addKeepRuntimeVisibleAnnotations()
        .addEnumUnboxingInspector(
            inspector ->
                inspector
                    .assertUnboxed(MyEnumParamMethod2.class, MyEnumRetMethod2.class)
                    .assertNotUnboxed(
                        MyEnum.class,
                        MyEnumDefault.class,
                        MyEnumArray.class,
                        MyEnumArrayDefault.class))
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("print", "1", "1", "1", "1", "1", "0", "0", "0", "0");
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface ClassAnnotationDefault {
    MyEnumDefault myEnumDefault() default MyEnumDefault.A;

    MyEnum myEnum();

    MyEnumArrayDefault[] myEnumArrayDefault() default {MyEnumArrayDefault.A, MyEnumArrayDefault.B};

    MyEnumArray[] myEnumArray();
  }

  @NoVerticalClassMerging
  @Retention(RetentionPolicy.RUNTIME)
  @interface ClassAnnotation {}

  enum MyEnumParamMethod2 {
    A,
    B
  }

  enum MyEnumRetMethod2 {
    A,
    B
  }

  enum MyEnumDefault {
    A,
    B
  }

  enum MyEnum {
    A,
    B
  }

  enum MyEnumArrayDefault {
    A,
    B,
    C
  }

  enum MyEnumArray {
    A,
    B,
    C
  }

  @NeverClassInline
  @ClassAnnotationDefault(
      myEnum = MyEnum.B,
      myEnumArray = {MyEnumArray.A, MyEnumArray.B})
  private static class ClassDefault {
    @NeverInline
    void print() {
      System.out.println("print");
    }
  }

  static class ClassAnnotationSub implements ClassAnnotation {

    @NeverInline
    @NeverPropagateValue
    MyEnumRetMethod2 enumMethod(MyEnumParamMethod2 param) {
      return param == MyEnumParamMethod2.A ? MyEnumRetMethod2.A : MyEnumRetMethod2.B;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
      return null;
    }
  }

  static class Main {
    public static void main(String[] args) {
      new ClassDefault().print();
      System.out.println(MyEnum.B.ordinal());
      System.out.println(MyEnumDefault.B.ordinal());
      System.out.println(MyEnumArray.B.ordinal());
      System.out.println(MyEnumArrayDefault.B.ordinal());
      ClassAnnotationDefault annotation =
          (ClassAnnotationDefault) ClassDefault.class.getDeclaredAnnotations()[0];
      System.out.println(annotation.myEnum().ordinal());
      System.out.println(annotation.myEnumArray()[0].ordinal());
      System.out.println(annotation.myEnumArrayDefault()[0].ordinal());
      System.out.println(annotation.myEnumDefault().ordinal());
      System.out.println(new ClassAnnotationSub().enumMethod(MyEnumParamMethod2.A).ordinal());
    }
  }
}
