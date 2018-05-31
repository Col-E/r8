// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.builders;

public class PairBuilder<F, S> {
  public F first;
  public S second = null;

  public PairBuilder<F, S> setFirst(F first) {
    System.out.println("[before] first = " + this.first);
    this.first = first;
    System.out.println("[after] first = " + this.first);
    return this;
  }

  public PairBuilder<F, S> setSecond(S second) {
    System.out.println("[before] second = " + this.second);
    this.second = second;
    System.out.println("[after] second = " + this.second);
    return this;
  }

  public Pair<F, S> build() {
    return new Pair<>(first, second);
  }
}

