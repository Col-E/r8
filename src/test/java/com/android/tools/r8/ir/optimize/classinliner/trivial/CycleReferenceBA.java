// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.trivial;

public class CycleReferenceBA {
  private String a;

  public CycleReferenceBA(String a) {
    this.a = a;
  }

  public void foo(int depth) {
    CycleReferenceAB ab = new CycleReferenceAB("depth=" + depth);
    System.out.println("CycleReferenceBA::foo(" + depth + ")");
    if (depth > 0) {
      ab.foo(depth - 1);
    }
  }

  @Override
  public String toString() {
    return "CycleReferenceBA(" + a + ")";
  }
}
