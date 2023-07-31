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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        // Collect and sort the annotations, as the order is not deterministic on Art (tested
        // on Art 14 Beta 3).
        List<String> annotations = new ArrayList<>();
        for (int j = 0; j < c.getAnnotations().length; j++) {
          annotations.add(c.getAnnotations()[j].toString());
        }
        annotations.sort(Comparator.naturalOrder());
        for (int j = 0; j < annotations.size(); j++) {
          System.out.println(annotations.get(j));
        }
      }
      System.out.println(Person.class.getDeclaredFields().length);
      List<Field> fields = new ArrayList<>();
      for (int i = 0; i < Person.class.getDeclaredFields().length; i++) {
        fields.add(Person.class.getDeclaredFields()[i]);
      }
      fields.sort(
          new Comparator<Field>() {
            @Override
            public int compare(Field o1, Field o2) {
              return o1.getName().compareTo(o2.getName());
            }
          });
      for (int i = 0; i < fields.size(); i++) {
        Field f = fields.get(i);
        System.out.println(f.getDeclaredAnnotations().length);
        List<String> annotations = new ArrayList<>();
        for (int j = 0; j < f.getDeclaredAnnotations().length; j++) {
          annotations.add(f.getAnnotations()[j].toString());
        }
        annotations.sort(Comparator.naturalOrder());
        for (int j = 0; j < annotations.size(); j++) {
          System.out.println(annotations.get(j));
        }
      }
    }
  }
}
