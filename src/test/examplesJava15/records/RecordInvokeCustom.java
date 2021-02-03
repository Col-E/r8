// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

public class RecordInvokeCustom {

  record Empty() {}

  record Person(String name, int age) {}

  public static void main(String[] args) {
    emptyTest();
    equalityTest();
    toStringTest();
  }

  private static void emptyTest() {
    Empty empty1 = new Empty();
    Empty empty2 = new Empty();
    System.out.println(empty1.toString());
    System.out.println(empty1.equals(empty2));
    System.out.println(empty1.hashCode() == empty2.hashCode());
    System.out.println(empty1.toString().equals(empty2.toString()));
  }

  private static void toStringTest() {
    Person janeDoe = new Person("Jane Doe", 42);
    System.out.println(janeDoe.toString());
  }

  private static void equalityTest() {
    Person jane1 = new Person("Jane Doe", 42);
    Person jane2 = new Person("Jane Doe", 42);
    String nonIdenticalString = "Jane " + (System.currentTimeMillis() > 0 ? "Doe" : "Zan");
    Person jane3 = new Person(nonIdenticalString, 42);
    Person bob = new Person("Bob", 42);
    Person youngJane = new Person("Jane Doe", 22);
    System.out.println(jane1.equals(jane2));
    System.out.println(jane1.toString().equals(jane2.toString()));
    System.out.println(nonIdenticalString == "Jane Doe"); // false.
    System.out.println(nonIdenticalString.equals("Jane Doe")); // true.
    System.out.println(jane1.equals(jane3));
    System.out.println(jane1.equals(bob));
    System.out.println(jane1.equals(youngJane));
  }
}
