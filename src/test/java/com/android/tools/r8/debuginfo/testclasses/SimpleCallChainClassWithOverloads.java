// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debuginfo.testclasses;

public class SimpleCallChainClassWithOverloads {

  public static void main(String[] args) {
    test();
  }

  public static void test() {
    test(System.currentTimeMillis());
  }

  public static void test(long value) {
    if (value > 0) {
      throw new RuntimeException("Hello World!");
    }
  }
}
