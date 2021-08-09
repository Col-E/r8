// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package pattern_matching_for_instanceof;

final class Main {
  public static void main(String[] args) {
    Object obj = "Hello, world!";
    if (obj instanceof String s) {
      System.out.println(s);
    }
  }
}
