// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public abstract class AbstractValue {

  public static BottomValue bottom() {
    return BottomValue.getInstance();
  }

  public static UnknownValue unknown() {
    return UnknownValue.getInstance();
  }

  public abstract boolean isNonTrivial();

  public boolean isSingleBoolean() {
    return false;
  }

  public boolean isBottom() {
    return false;
  }

  public boolean isFalse() {
    return false;
  }

  public boolean isTrue() {
    return false;
  }

  public final boolean isNull() {
    return isFalse();
  }

  public final boolean isZero() {
    return isFalse();
  }

  /**
   * Returns true if this abstract value represents a single concrete value (i.e., the
   * concretization of this abstract value has size 1).
   */
  public boolean isSingleValue() {
    return false;
  }

  public SingleValue asSingleValue() {
    return null;
  }

  public boolean isSingleConstValue() {
    return false;
  }

  public SingleConstValue asSingleConstValue() {
    return null;
  }

  public boolean isSingleConstClassValue() {
    return false;
  }

  public SingleConstClassValue asSingleConstClassValue() {
    return null;
  }

  public boolean isSingleFieldValue() {
    return false;
  }

  public SingleFieldValue asSingleFieldValue() {
    return null;
  }

  public boolean isSingleNumberValue() {
    return false;
  }

  public SingleNumberValue asSingleNumberValue() {
    return null;
  }

  public boolean isSingleStringValue() {
    return false;
  }

  public SingleStringValue asSingleStringValue() {
    return null;
  }

  public boolean isSingleDexItemBasedStringValue() {
    return false;
  }

  public SingleDexItemBasedStringValue asSingleDexItemBasedStringValue() {
    return null;
  }

  public boolean isUnknown() {
    return false;
  }

  public boolean isNullOrAbstractValue() {
    return false;
  }

  public NullOrAbstractValue asNullOrAbstractValue() {
    return null;
  }

  public boolean isConstantOrNonConstantNumberValue() {
    return false;
  }

  public ConstantOrNonConstantNumberValue asConstantOrNonConstantNumberValue() {
    return null;
  }

  public boolean isNonConstantNumberValue() {
    return false;
  }

  public NonConstantNumberValue asNonConstantNumberValue() {
    return null;
  }

  public boolean isNumberFromIntervalValue() {
    return false;
  }

  public NumberFromIntervalValue asNumberFromIntervalValue() {
    return null;
  }

  public boolean isNumberFromSetValue() {
    return false;
  }

  public NumberFromSetValue asNumberFromSetValue() {
    return null;
  }

  public AbstractValue join(AbstractValue other, AbstractValueFactory factory, DexType type) {
    return join(other, factory, type.isReferenceType(), false);
  }

  // TODO(b/196321452): Clean this up, in particular, replace the "allow" parameters by a
  //  configuration object.
  public AbstractValue join(
      AbstractValue other,
      AbstractValueFactory factory,
      boolean allowNullOrAbstractValue,
      boolean allowNonConstantNumbers) {
    if (isBottom() || other.isUnknown()) {
      return other;
    }
    if (isUnknown() || other.isBottom()) {
      return this;
    }
    if (equals(other)) {
      return this;
    }
    if (allowNullOrAbstractValue) {
      if (isNull()) {
        return NullOrAbstractValue.create(other);
      }
      if (other.isNull()) {
        return NullOrAbstractValue.create(this);
      }
    }
    if (allowNonConstantNumbers
        && isConstantOrNonConstantNumberValue()
        && other.isConstantOrNonConstantNumberValue()) {
      NumberFromSetValue.Builder numberFromSetValueBuilder;
      if (isSingleNumberValue()) {
        numberFromSetValueBuilder = NumberFromSetValue.builder(asSingleNumberValue());
      } else {
        assert isNumberFromSetValue();
        numberFromSetValueBuilder = asNumberFromSetValue().instanceBuilder();
      }
      if (other.isSingleNumberValue()) {
        numberFromSetValueBuilder.addInt(other.asSingleNumberValue().getIntValue());
      } else {
        assert other.isNumberFromSetValue();
        numberFromSetValueBuilder.addInts(other.asNumberFromSetValue());
      }
      return numberFromSetValueBuilder.build(factory);
    }
    return UnknownValue.getInstance();
  }

  public abstract AbstractValue rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens lens);

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();

  @Override
  public abstract String toString();
}
