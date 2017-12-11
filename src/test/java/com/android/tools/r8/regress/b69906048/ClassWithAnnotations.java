// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b69906048;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

public class ClassWithAnnotations {

  public static void aMethod(int param1, @AnAnnotation Object param2) {
    // Intentionally left empty.
  }

  public static void main(String[] args) {
    try {
      Method aMethod = ClassWithAnnotations.class
          .getDeclaredMethod("aMethod", int.class, Object.class);
      Annotation[][] parameterAnnotations = aMethod.getParameterAnnotations();
      for (Annotation[] annotations : parameterAnnotations) {
        for (Annotation annotation : annotations) {
          System.out.print(annotation);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
