// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugaring.interfacemethods.methodparameters;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Parameter;

public interface I {

  default void zeroArgsDefault() {
    System.out.println(new Object() {}.getClass().getEnclosingMethod().getParameters().length);
  }

  default void oneArgDefault(@RuntimeAnnotation1(n = 0) int a) {
    Parameter[] parameters = new Object() {}.getClass().getEnclosingMethod().getParameters();
    System.out.println(parameters.length);
    for (Parameter parameter : parameters) {
      System.out.println(parameter.getName() + ": " + parameter.getAnnotations().length);
    }
  }

  default void twoArgDefault(
      @RuntimeAnnotation1(n = 1) int a,
      @RuntimeAnnotation1(n = 2) @RuntimeAnnotation2(n = 2) int b) {
    Parameter[] parameters = new Object() {}.getClass().getEnclosingMethod().getParameters();
    System.out.println(parameters.length);
    for (Parameter parameter : parameters) {
      System.out.println(parameter.getName() + ": " + parameter.getAnnotations().length);
    }
  }

  static void zeroArgStatic() {
    Parameter[] parameters = new Object() {}.getClass().getEnclosingMethod().getParameters();
    System.out.println(parameters.length);
    for (Parameter parameter : parameters) {
      System.out.println(parameter.getName() + ": " + parameter.getAnnotations().length);
    }
  }

  static void oneArgStatic(@RuntimeAnnotation1(n = 0) int a) {
    Parameter[] parameters = new Object() {}.getClass().getEnclosingMethod().getParameters();
    System.out.println(parameters.length);
    for (Parameter parameter : parameters) {
      System.out.println(parameter.getName() + ": " + parameter.getAnnotations().length);
    }
  }

  static void twoArgsStatic(
      @RuntimeAnnotation1(n = 1) int a,
      @RuntimeAnnotation1(n = 2) @RuntimeAnnotation2(n = 2) int b) {
    Parameter[] parameters = new Object() {}.getClass().getEnclosingMethod().getParameters();
    System.out.println(parameters.length);
    for (Parameter parameter : parameters) {
      System.out.println(parameter.getName() + ": " + parameter.getAnnotations().length);
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface RuntimeAnnotation1 {
    int n();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface RuntimeAnnotation2 {
    int n();
  }
}
