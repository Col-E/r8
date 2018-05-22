// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.trivial;

public class Iface2Impl implements Iface2 {
  final String value;

  public Iface2Impl(String value) {
    this.value = value;
  }

  @Override
  public void foo() {
    System.out.println(value);
  }
}
