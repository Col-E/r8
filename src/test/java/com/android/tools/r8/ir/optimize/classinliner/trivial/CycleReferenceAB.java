// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.trivial;

public class CycleReferenceAB {
  private String a;

  public CycleReferenceAB(String a) {
    this.a = a;
  }

  public void foo(int depth) {
    CycleReferenceBA ba = new CycleReferenceBA("depth=" + depth);
    System.out.println("CycleReferenceAB::foo(" + depth + ")");
    if (depth > 0) {
      ba.foo(depth - 1);
    }
  }

  @Override
  public String toString() {
    return "CycleReferenceAB(" + a + ")";
  }
}
