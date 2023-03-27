// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

import java.lang.reflect.RecordComponent;

public class RecordWithSignature {

  record Person<N extends CharSequence, A>(N name, A age) {}

  public static void main(String[] args) {
    Person<String, Integer> janeDoe = new Person<>("Jane Doe", 42);
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
        System.out.println(c.getGenericSignature());
        System.out.println(c.getAnnotations().length);
      }
    }
  }
}
