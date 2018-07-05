// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.testrules;

public class Main {

  public static void main(String[] args) {
    System.out.println(A.method());
    System.out.println(new B().method());
    System.out.println(C.x());
  }
}
