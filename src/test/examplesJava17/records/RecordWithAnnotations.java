// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;

public class RecordWithAnnotations {

  @Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
  @Retention(RetentionPolicy.RUNTIME)
  @interface Annotation {
    String value();
  }

  @Target(ElementType.FIELD)
  @Retention(RetentionPolicy.RUNTIME)
  @interface AnnotationFieldOnly {
    String value();
  }

  @Target(ElementType.RECORD_COMPONENT)
  @Retention(RetentionPolicy.RUNTIME)
  @interface AnnotationRecordComponentOnly {
    String value();
  }

  record Person(
      @Annotation("a") @AnnotationFieldOnly("b") @AnnotationRecordComponentOnly("c") String name,
      @Annotation("x") @AnnotationFieldOnly("y") @AnnotationRecordComponentOnly("z") int age) {}

  public static void main(String[] args) {
    Person janeDoe = new Person("Jane Doe", 42);
    System.out.println(janeDoe.name);
    System.out.println(janeDoe.age);
    System.out.println(janeDoe.name());
    System.out.println(janeDoe.age());
    try {
      Class.class.getDeclaredMethod("isRecord");
    } catch (NoSuchMethodException e) {
      System.out.println("Class.isRecord not present");
      return;
    }
    System.out.println(Person.class.isRecord());
    if (Person.class.isRecord()) {
      System.out.println(Person.class.getRecordComponents().length);
      for (int i = 0; i < Person.class.getRecordComponents().length; i++) {
        RecordComponent c = Person.class.getRecordComponents()[i];
        System.out.println(c.getName());
        System.out.println(c.getType().getName());
        System.out.println(c.getGenericSignature() == null);
        System.out.println(c.getAnnotations().length);
        for (int j = 0; j < c.getAnnotations().length; j++) {
          System.out.println(c.getAnnotations()[j]);
        }
      }
      System.out.println(Person.class.getDeclaredFields().length);
      for (int i = 0; i < Person.class.getDeclaredFields().length; i++) {
        Field f = Person.class.getDeclaredFields()[i];
        System.out.println(f.getDeclaredAnnotations().length);
        for (int j = 0; j < f.getDeclaredAnnotations().length; j++) {
          System.out.println(f.getAnnotations()[j]);
        }
      }
    }
  }
}
