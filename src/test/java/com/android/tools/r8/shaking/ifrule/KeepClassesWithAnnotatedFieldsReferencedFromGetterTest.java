// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule;


import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

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
public class KeepClassesWithAnnotatedFieldsReferencedFromGetterTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public KeepClassesWithAnnotatedFieldsReferencedFromGetterTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(KeepClassesWithAnnotatedFieldsReferencedFromGetterTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-if class *",
            "-keepclasseswithmembers class <1> {",
            "  <init>(...);",
            "  @" + typeName(MyAnnotation.class) + " <fields>;",
            "}")
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(LiveDataClass.class), isPresent());
              assertThat(inspector.clazz(DeadDataClass.class), not(isPresent()));
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      LiveDataClass obj = (LiveDataClass) getDataClass().getDeclaredConstructor().newInstance();
      setField(obj, "Hello world!");
      System.out.println(obj.getField());
    }

    @NeverInline
    @NeverPropagateValue
    static Class<?> getDataClass() {
      return LiveDataClass.class;
    }

    @NeverInline
    static void setField(Object object, String value) throws Exception {
      getDataClass().getDeclaredField("field").set(object, value);
    }
  }

  static class LiveDataClass {

    @MyAnnotation String field;

    String getField() {
      return field;
    }
  }

  static class DeadDataClass {

    @MyAnnotation String field;

    String getField() {
      return field;
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  @interface MyAnnotation {}
}
