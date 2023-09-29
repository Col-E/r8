// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class UnknownValue extends AbstractValue {

  private static final UnknownValue INSTANCE = new UnknownValue();

  private UnknownValue() {}

  public static UnknownValue getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isNonTrivial() {
    return false;
  }

  @Override
  public boolean isUnknown() {
    return true;
  }

  @Override
  public AbstractValue rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, DexType newType, GraphLens lens, GraphLens codeLens) {
    return this;
  }

  @Override
  public boolean equals(Object o) {
    return this == o;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public String toString() {
    return "UnknownValue";
  }
}
