// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class EmptyRecordAnnotation {

  record Empty() {}

  @Retention(RetentionPolicy.RUNTIME)
  @interface ClassAnnotation {
    Class<? extends Record> theClass();
  }

  @ClassAnnotation(theClass = Record.class)
  public static void annotatedMethod1() {}

  @ClassAnnotation(theClass = Empty.class)
  public static void annotatedMethod2() {}

  public static void main(String[] args) throws Exception {
    Class<?> annotatedMethod1Content =
        EmptyRecordAnnotation.class
            .getDeclaredMethod("annotatedMethod1")
            .getAnnotation(ClassAnnotation.class)
            .theClass();
    System.out.println(annotatedMethod1Content);
    Class<?> annotatedMethod2Content =
        EmptyRecordAnnotation.class
            .getDeclaredMethod("annotatedMethod2")
            .getAnnotation(ClassAnnotation.class)
            .theClass();
    System.out.println(annotatedMethod2Content);
  }
}
