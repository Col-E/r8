// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package regress_65104300;

public class Regress {
  // We correctly deduce that the array put cannot throw. However, we had a bug
  // where we did not remove the handler but we allowed lowering the const 0 below
  // the array put which makes it unavailable in the handler block.
  public static void main(String[] args) {
    Object[] objects = new Object[10];
    Object o = new Object();
    try {
      objects[4] = o;
      System.out.println(0);
    } catch (Exception e) {
      System.out.println(0);
    }
  }
}
