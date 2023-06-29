// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

public class SimpleRecord {

  record Person(String name, int age) {}

  public static void main(String[] args) {
    Person janeDoe = new Person("Jane Doe", 42);
    System.out.println(janeDoe.name);
    System.out.println(janeDoe.age);
    System.out.println(janeDoe.name());
    System.out.println(janeDoe.age());

    // Test equals with self.
    System.out.println(janeDoe.equals(janeDoe));

    // Test equals with structurally equals Person.
    Person otherJaneDoe = new Person("Jane Doe", 42);
    System.out.println(janeDoe.equals(otherJaneDoe));
    System.out.println(otherJaneDoe.equals(janeDoe));

    // Test equals with not-structually equals Person.
    Person johnDoe = new Person("John Doe", 42);
    System.out.println(janeDoe.equals(johnDoe));
    System.out.println(johnDoe.equals(janeDoe));

    // Test equals with Object and null.
    System.out.println(janeDoe.equals(new Object()));
    System.out.println(janeDoe.equals(null));
  }
}
