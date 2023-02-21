// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepClassesWithAnnotatedFieldsTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public KeepClassesWithAnnotatedFieldsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(KeepClassesWithAnnotatedFieldsTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-if class * { @" + typeName(MyAnnotation.class) + " <fields>; }",
            "-keep class <1> { <init>(); }",
            "-keepclassmembers class * {",
            "  @" + typeName(MyAnnotation.class) + " <fields>;",
            "}")
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      DataClass obj = (DataClass) getDataClass().getDeclaredConstructor().newInstance();
      setField(obj, "Hello world!");
      System.out.println(obj.field);
    }

    @NeverInline
    @NeverPropagateValue
    static Class<?> getDataClass() {
      return DataClass.class;
    }

    @NeverInline
    static void setField(Object object, String value) throws Exception {
      getDataClass().getDeclaredField("field").set(object, value);
    }
  }

  static class DataClass {

    @MyAnnotation
    String field;
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  @interface MyAnnotation {}
}
