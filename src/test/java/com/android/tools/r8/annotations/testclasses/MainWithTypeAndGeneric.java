// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.annotations.testclasses;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class MainWithTypeAndGeneric {

  public static void main(String[] args) throws Exception {
    Class<TestClassWithTypeAndGenericAnnotations> testClass =
        TestClassWithTypeAndGenericAnnotations.class;
    printAnnotation("Class", testClass.getAnnotation(NotNullTestRuntime.class));
    printAnnotation("Class", testClass.getAnnotation(NotNullTestClass.class));
    printAnnotatedType("Extends", testClass.getAnnotatedSuperclass());
    for (AnnotatedType annotatedInterface : testClass.getAnnotatedInterfaces()) {
      printAnnotatedType("Implements", annotatedInterface);
    }
    Field field = testClass.getDeclaredField("field");
    printAnnotation("Field", field.getAnnotation(NotNullTestRuntime.class));
    printAnnotation("Field", field.getAnnotation(NotNullTestClass.class));
    printAnnotatedType("Field", field.getAnnotatedType());
    Method method = testClass.getDeclaredMethod("method", int.class, List.class, Object.class);
    printAnnotation("Method", method.getAnnotation(NotNullTestRuntime.class));
    printAnnotation("Method", method.getAnnotation(NotNullTestClass.class));
    printAnnotatedType("MethodReturnType", method.getAnnotatedReturnType());
    for (Annotation[] parameterAnnotation : method.getParameterAnnotations()) {
      for (Annotation annotation : parameterAnnotation) {
        printAnnotation("MethodParameter", annotation);
      }
    }
    for (int i = 0; i < method.getAnnotatedParameterTypes().length; i++) {
      printAnnotatedType("MethodParameter at " + i, method.getAnnotatedParameterTypes()[i]);
    }
    for (int i = 0; i < method.getAnnotatedExceptionTypes().length; i++) {
      printAnnotatedType("MethodException at " + i, method.getAnnotatedExceptionTypes()[i]);
    }
    System.out.println("Hello World!");
  }

  public static void printAnnotation(String name, Annotation annotation) {
    System.out.println(
        "printAnnotation - "
            + name
            + ": "
            + (annotation == null ? "NULL" : annotation.annotationType().getName()));
  }

  public static void printAnnotatedType(String name, AnnotatedType annotatedType) {
    for (int i = 0; i < annotatedType.getAnnotations().length; i++) {
      printAnnotation(name + "(" + i + ")", annotatedType.getAnnotations()[i]);
    }
  }
}
