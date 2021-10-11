// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

/** A KnownLengthArrayValue implicitly implies the value is non null. */
public class KnownLengthArrayValue extends AbstractValue {

  private final int length;

  public KnownLengthArrayValue(int length) {
    this.length = length;
  }

  public int getLength() {
    return length;
  }

  @Override
  public boolean isKnownLengthArrayValue() {
    return true;
  }

  @Override
  public KnownLengthArrayValue asKnownLengthArrayValue() {
    return this;
  }

  @Override
  public boolean isNonTrivial() {
    return true;
  }

  @Override
  public AbstractValue rewrittenWithLens(AppView<AppInfoWithLiveness> appView, GraphLens lens) {
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
    return "KnownLengthArrayValue(len=" + length + ")";
  }
}
