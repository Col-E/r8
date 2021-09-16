// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

public class UnusedRecordMethod {

  Record unusedInstanceMethod(Record unused) {
    return null;
  }

  void printHello() {
    System.out.println("Hello!");
  }

  public static void main(String[] args) {
    new UnusedRecordMethod().printHello();
  }
}
