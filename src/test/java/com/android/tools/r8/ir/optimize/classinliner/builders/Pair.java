// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.builders;

public class Pair<F, S> {
  public final F first;
  public final S second;

  public Pair(F first, S second) {
    this.first = first;
    this.second = second;
  }

  @Override
  public String toString() {
    return "Pair(" +
        (first == null ? "<null>" : first) + ", " +
        (second == null ? "<null>" : second) + ")";
  }
}
