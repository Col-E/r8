// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

public class ArraySimplificationLineNumberTest {

  public String[] foo(boolean argument) {
    String[] result;
    if (argument) {
      result = new String[2]; result[0] = "abc";
      result[1] = "xyz";
    } else {
      result = new String[2]; result[0] = "abc";
      result[1] = "xyz";
    }
    return result;
  }

  public int[] bar(boolean argument) {
    int[] result;
    if (argument) {
      result = new int[2]; result[0] = 123;
      result[1] = 456;
    } else {
      result = new int[2]; result[0] = 123;
      result[1] = 456;
    }
    return result;
  }

  public static void main(String[] args) {
    new ArraySimplificationLineNumberTest().foo(true);
    new ArraySimplificationLineNumberTest().bar(true);
  }
}
