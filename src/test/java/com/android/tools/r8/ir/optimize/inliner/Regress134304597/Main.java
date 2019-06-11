// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner.Regress134304597;

public class Main {
  public static void main(String[] args) {
    Test a = new Test();
    if (args.length > 10) {
      a.printValue();
    }

    System.out.println(a.getValue());
  }
}
