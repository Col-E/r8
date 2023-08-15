// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug.classes;

public class Exceptions {

  private static void catchException() {
    try {
      throwNullPointerException();
    } catch (NullPointerException e) {
      System.out.println("Caught NPE");
    }
  }

  private static void throwNullPointerException() {
    throw new NullPointerException();
  }

  public static void main(String[] args) {
    catchException();
  }
}
