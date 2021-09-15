// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

public class RecordInstanceOf {

  record Empty() {}

  record Person(String name, int age) {}

  public static void main(String[] args) {
    Empty empty = new Empty();
    Person janeDoe = new Person("Jane Doe", 42);
    Object o = new Object();
    System.out.println(janeDoe instanceof java.lang.Record);
    System.out.println(empty instanceof java.lang.Record);
    System.out.println(o instanceof java.lang.Record);
  }
}
