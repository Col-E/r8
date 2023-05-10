// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

public class RecordBlog {

  record Person(String name, int age) {}

  public static void main(String[] args) {
    Person jane = new Person("Jane", 42);
    System.out.println(jane.toString());
    Person john = new Person("John", 42);
    System.out.println(john.toString());
  }
}
