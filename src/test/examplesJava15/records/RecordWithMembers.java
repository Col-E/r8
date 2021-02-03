// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

public class RecordWithMembers {


  record PersonWithConstructors(String name, int age) {

    public PersonWithConstructors(String name, int age) {
      this.name = name + "X";
      this.age = age;
    }

    public PersonWithConstructors(String name) {
      this(name, -1);
    }
  }

  record PersonWithMethods(String name, int age) {
    public static void staticPrint() {
      System.out.println("print");
    }

    @Override
    public String toString() {
      return name + age;
    }
  }

  record PersonWithFields(String name, int age) {

    // Extra instance fields are not allowed on records.
    public static String globalName;

  }

  public static void main(String[] args) {
    personWithConstructorTest();
    personWithMethodsTest();
    personWithFieldsTest();
  }

  private static void personWithConstructorTest() {
    PersonWithConstructors bob = new PersonWithConstructors("Bob", 43);
    System.out.println(bob.name);
    System.out.println(bob.age);
    System.out.println(bob.name());
    System.out.println(bob.age());
    PersonWithConstructors felix = new PersonWithConstructors("Felix");
    System.out.println(felix.name);
    System.out.println(felix.age);
    System.out.println(felix.name());
    System.out.println(felix.age());
  }

  private static void personWithMethodsTest() {
    PersonWithMethods.staticPrint();
    PersonWithMethods bob = new PersonWithMethods("Bob", 43);
    System.out.println(bob.toString());
  }

  private static void personWithFieldsTest() {
    PersonWithFields.globalName = "extra";
    System.out.println(PersonWithFields.globalName);
  }
}
