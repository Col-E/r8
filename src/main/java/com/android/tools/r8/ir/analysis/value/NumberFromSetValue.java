// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.OptionalBool;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;

public class NumberFromSetValue extends NonConstantNumberValue {

  private static final int MAX_SIZE = 30;

  private final IntSet numbers;

  private NumberFromSetValue(IntSet numbers) {
    this.numbers = numbers;
  }

  static Builder builder() {
    return new Builder();
  }

  static Builder builder(SingleNumberValue singleNumberValue) {
    return new Builder().addInt(singleNumberValue.getIntValue());
  }

  Builder instanceBuilder() {
    return new Builder(this);
  }

  @Override
  public boolean containsInt(int value) {
    return numbers.contains(value);
  }

  @Override
  public long getAbstractionSize() {
    return numbers.size();
  }

  @Override
  public boolean isNumberFromSetValue() {
    return true;
  }

  @Override
  public NumberFromSetValue asNumberFromSetValue() {
    return this;
  }

  @Override
  public boolean isNonTrivial() {
    return true;
  }

  @Override
  public OptionalBool isSubsetOf(int[] values) {
    assert ArrayUtils.isSorted(values);
    for (int number : numbers) {
      if (Arrays.binarySearch(values, number) < 0) {
        return OptionalBool.FALSE;
      }
    }
    return OptionalBool.TRUE;
  }

  @Override
  public boolean mayOverlapWith(ConstantOrNonConstantNumberValue other) {
    if (other.isSingleNumberValue()) {
      return containsInt(other.asSingleNumberValue().getIntValue());
    }
    assert other.isNonConstantNumberValue();
    for (int number : numbers) {
      if (other.containsInt(number)) {
        return true;
      }
    }
    return false;
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
    NumberFromSetValue numberFromSetValue = (NumberFromSetValue) o;
    return numbers.equals(numberFromSetValue.numbers);
  }

  @Override
  public int hashCode() {
    return numbers.hashCode();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("NumberFromSetValue(");
    IntIterator iterator = numbers.iterator();
    builder.append(iterator.nextInt());
    while (iterator.hasNext()) {
      builder.append(", ").append(iterator.nextInt());
    }
    return builder.append(")").toString();
  }

  static class Builder {

    private IntSet numbers;

    Builder() {
      numbers = new IntArraySet();
    }

    Builder(NumberFromSetValue numberFromSetValue) {
      numbers = new IntArraySet(numberFromSetValue.numbers);
    }

    Builder addInt(int number) {
      if (numbers != null) {
        assert numbers.size() <= MAX_SIZE;
        if (numbers.add(number) && numbers.size() > MAX_SIZE) {
          numbers = null;
        }
      }
      return this;
    }

    Builder addInts(NumberFromSetValue numberFromSetValue) {
      if (numbers != null) {
        assert numbers.size() <= MAX_SIZE;
        if (numbers.addAll(numberFromSetValue.numbers) && numbers.size() > MAX_SIZE) {
          numbers = null;
        }
      }
      return this;
    }

    AbstractValue build(AbstractValueFactory abstractValueFactory) {
      if (numbers != null) {
        assert !numbers.isEmpty();
        assert numbers.size() <= MAX_SIZE;
        if (numbers.size() == 1) {
          return abstractValueFactory.createSingleNumberValue(numbers.iterator().nextInt());
        }
        return new NumberFromSetValue(numbers);
      }
      return UnknownValue.getInstance();
    }
  }
}
