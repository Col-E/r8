// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package regress_62300145;

import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;

public class Regress {
  @Retention(RetentionPolicy.RUNTIME)
  public @interface A {
  }

  @Retention(CLASS)
  @Target({ElementType.PARAMETER})
  public @interface B {
  }

  public class InnerClass {
    public InnerClass(@A @B String p1, @A String p2, @B String p3) { }
  }

  public static void main(String[] args) throws NoSuchMethodException {
    Constructor<InnerClass> constructor = InnerClass.class.getDeclaredConstructor(
        Regress.class, String.class, String.class, String.class);
    Annotation[][] annotations = constructor.getParameterAnnotations();
    int index = 0;
    for (int i = 0; i < annotations.length; i++) {
      // TODO(b/67936230): Java 8 and Java 9 runtime does not have the same behavior regarding
      // implicit parameter such as 'outer this' for instance. Disable this test on Java 9 runtime
      // due to this divergence.
      if (System.getProperty("java.specification.version").equals("9") && i == 0) {
        continue;
      }
      System.out.print(index++ + ": ");
      for (Annotation annotation : annotations[i]) {
        System.out.println(annotation);
      }
    }
  }
}
