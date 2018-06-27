// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.accessrelaxation.privateinstance;

public class TestMain {

  public static void main(String[] args) {
    new Base().dump();
    new Sub1().dump();
    new Sub2().dump();
  }
}
