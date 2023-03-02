// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

public class RecordInterface {

  interface Human {
    default void printHuman() {
      System.out.println("Human");
    }
  }

  record Person(String name, int age) implements Human {}

  public static void main(String[] args) {
    Person janeDoe = new Person("Jane Doe", 42);
    janeDoe.printHuman();
  }
}
