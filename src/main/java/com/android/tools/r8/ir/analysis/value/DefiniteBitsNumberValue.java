// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.OptionalBool;
import java.util.Objects;

public class DefiniteBitsNumberValue extends NonConstantNumberValue {

  private final int definitelySetBits;
  private final int definitelyUnsetBits;

  public DefiniteBitsNumberValue(int definitelySetBits, int definitelyUnsetBits) {
    assert (definitelySetBits & definitelyUnsetBits) == 0;
    this.definitelySetBits = definitelySetBits;
    this.definitelyUnsetBits = definitelyUnsetBits;
  }

  @Override
  public boolean maybeContainsInt(int value) {
    // If a definitely set bit is unset in value, then no.
    if ((definitelySetBits & ~value) != 0) {
      return false;
    }
    // If a definitely unset bit is set in value, then no.
    if ((definitelyUnsetBits & value) != 0) {
      return false;
    }
    return true;
  }

  @Override
  public long getAbstractionSize() {
    return Long.MAX_VALUE;
  }

  @Override
  public int getDefinitelySetIntBits() {
    return definitelySetBits;
  }

  @Override
  public int getDefinitelyUnsetIntBits() {
    return definitelyUnsetBits;
  }

  @Override
  public long getMinInclusive() {
    return Integer.MIN_VALUE;
  }

  @Override
  public boolean hasDefinitelySetAndUnsetBitsInformation() {
    return true;
  }

  @Override
  public boolean isDefiniteBitsNumberValue() {
    return true;
  }

  @Override
  public DefiniteBitsNumberValue asDefiniteBitsNumberValue() {
    return this;
  }

  @Override
  public boolean isNonTrivial() {
    return true;
  }

  @Override
  public OptionalBool isSubsetOf(int[] values) {
    return OptionalBool.unknown();
  }

  public AbstractValue join(
      AbstractValueFactory abstractValueFactory, DefiniteBitsNumberValue definiteBitsNumberValue) {
    return join(
        abstractValueFactory,
        definiteBitsNumberValue.definitelySetBits,
        definiteBitsNumberValue.definitelyUnsetBits);
  }

  public AbstractValue join(
      AbstractValueFactory abstractValueFactory, SingleNumberValue singleNumberValue) {
    return join(
        abstractValueFactory,
        singleNumberValue.getDefinitelySetIntBits(),
        singleNumberValue.getDefinitelyUnsetIntBits());
  }

  public AbstractValue join(
      AbstractValueFactory abstractValueFactory,
      int otherDefinitelySetBits,
      int otherDefinitelyUnsetBits) {
    if (definitelySetBits == otherDefinitelySetBits
        && definitelyUnsetBits == otherDefinitelyUnsetBits) {
      return this;
    }
    return abstractValueFactory.createDefiniteBitsNumberValue(
        definitelySetBits & otherDefinitelySetBits, definitelyUnsetBits & otherDefinitelyUnsetBits);
  }

  @Override
  public boolean mayOverlapWith(ConstantOrNonConstantNumberValue other) {
    return true;
  }

  @Override
  public AbstractValue rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens lens, GraphLens codeLens) {
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || o.getClass() != getClass()) {
      return false;
    }
    DefiniteBitsNumberValue definiteBitsNumberValue = (DefiniteBitsNumberValue) o;
    return definitelySetBits == definiteBitsNumberValue.definitelySetBits
        && definitelyUnsetBits == definiteBitsNumberValue.definitelyUnsetBits;
  }

  @Override
  public int hashCode() {
    int hash = 31 * (31 * (31 + definitelySetBits) + definitelyUnsetBits);
    assert hash == Objects.hash(definitelySetBits, definitelyUnsetBits);
    return hash;
  }

  @Override
  public String toString() {
    return "DefiniteBitsNumberValue(set: "
        + Integer.toBinaryString(definitelySetBits)
        + "; unset: "
        + Integer.toBinaryString(definitelyUnsetBits)
        + ")";
  }
}
