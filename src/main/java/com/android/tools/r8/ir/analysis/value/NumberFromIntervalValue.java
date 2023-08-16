// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.OptionalBool;
import java.util.Objects;

public class NumberFromIntervalValue extends NonConstantNumberValue {

  private final long minInclusive;
  private final long maxInclusive;

  public NumberFromIntervalValue(long minInclusive, long maxInclusive) {
    assert maxInclusive > minInclusive;
    this.minInclusive = minInclusive;
    this.maxInclusive = maxInclusive;
  }

  @Override
  public boolean maybeContainsInt(int value) {
    return minInclusive <= value && value <= maxInclusive;
  }

  @Override
  public long getAbstractionSize() {
    return maxInclusive - minInclusive + 1;
  }

  @Override
  public long getMinInclusive() {
    return minInclusive;
  }

  public long getMaxInclusive() {
    return maxInclusive;
  }

  @Override
  public boolean isNumberFromIntervalValue() {
    return true;
  }

  @Override
  public NumberFromIntervalValue asNumberFromIntervalValue() {
    return this;
  }

  @Override
  public boolean isNonTrivial() {
    return true;
  }

  @Override
  public OptionalBool isSubsetOf(int[] values) {
    // Not implemented.
    return OptionalBool.unknown();
  }

  @Override
  public boolean mayOverlapWith(ConstantOrNonConstantNumberValue other) {
    if (other.isDefiniteBitsNumberValue()) {
      // Conservatively return true.
      return true;
    }
    if (other.isSingleNumberValue()) {
      return maybeContainsInt(other.asSingleNumberValue().getIntValue());
    }
    if (other.isNumberFromIntervalValue()) {
      return mayOverlapWith(other.asNumberFromIntervalValue());
    }
    assert other.isNumberFromSetValue();
    return mayOverlapWith(other.asNumberFromSetValue());
  }

  public boolean mayOverlapWith(NumberFromIntervalValue other) {
    return minInclusive <= other.maxInclusive && maxInclusive >= other.minInclusive;
  }

  public boolean mayOverlapWith(NumberFromSetValue other) {
    return other.mayOverlapWith(this);
  }

  @Override
  public AbstractValue rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens lens, GraphLens codeLens) {
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || o.getClass() != getClass()) {
      return false;
    }
    NumberFromIntervalValue numberFromIntervalValue = (NumberFromIntervalValue) o;
    return minInclusive == numberFromIntervalValue.minInclusive
        && maxInclusive == numberFromIntervalValue.maxInclusive;
  }

  @Override
  public int hashCode() {
    int hash = 31 * (31 * (31 + Long.hashCode(minInclusive)) + Long.hashCode(maxInclusive));
    assert hash == Objects.hash(minInclusive, maxInclusive);
    return hash;
  }

  @Override
  public String toString() {
    return "NumberFromIntervalValue([" + minInclusive + "; " + maxInclusive + "])";
  }
}
