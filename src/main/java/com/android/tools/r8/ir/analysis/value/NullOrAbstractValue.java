// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class NullOrAbstractValue extends AbstractValue {

  private final AbstractValue value;

  private NullOrAbstractValue(AbstractValue value) {
    this.value = value;
  }

  public static AbstractValue create(AbstractValue value) {
    if (value.isBottom() || value.isUnknown() || value.isNull() || value.isNullOrAbstractValue()) {
      return value;
    }
    return new NullOrAbstractValue(value);
  }

  @Override
  public boolean isNonTrivial() {
    return true;
  }

  @Override
  public boolean isNullOrAbstractValue() {
    return true;
  }

  @Override
  public NullOrAbstractValue asNullOrAbstractValue() {
    return this;
  }

  public AbstractValue getNonNullValue() {
    return value;
  }

  @Override
  public NullOrAbstractValue rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, DexType newType, GraphLens lens, GraphLens codeLens) {
    return new NullOrAbstractValue(value.rewrittenWithLens(appView, newType, lens, codeLens));
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    return this.getClass() == o.getClass() && value.equals(((NullOrAbstractValue) o).value);
  }

  @Override
  public int hashCode() {
    return value.hashCode() * 7;
  }

  @Override
  public String toString() {
    return "Null or " + value.toString();
  }
}
