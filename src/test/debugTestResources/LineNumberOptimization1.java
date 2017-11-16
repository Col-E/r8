// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

public class LineNumberOptimization1 {
  private static void callThisFromSameFile() {
    System.out.println("callThisFromSameFile");
    LineNumberOptimization2.callThisFromAnotherFile();
  }

  private static void callThisFromSameFile(int a) {
    System.out.println("callThisFromSameFile second overload");
  }

  private static void callThisFromSameFile(int a, int b) {
    System.out.println("callThisFromSameFile third overload");
  }

  public static void main(String[] args) {
    callThisFromSameFile();
    callThisFromSameFile(1);
    callThisFromSameFile(1, 2);
  }
}
