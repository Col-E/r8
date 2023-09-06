// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

/** A KnownLengthArrayValue implicitly implies the value is non null. */
public class StatefulObjectValue extends AbstractValue {

  private final ObjectState state;

  StatefulObjectValue(ObjectState state) {
    assert !state.isEmpty();
    this.state = state;
  }

  public static AbstractValue create(ObjectState objectState) {
    return objectState.isEmpty()
        ? UnknownValue.getInstance()
        : new StatefulObjectValue(objectState);
  }

  @Override
  public boolean isNonTrivial() {
    return true;
  }

  @Override
  public boolean isStatefulObjectValue() {
    return true;
  }

  @Override
  public StatefulObjectValue asStatefulObjectValue() {
    return this;
  }

  @Override
  public boolean hasKnownArrayLength() {
    return getObjectState().hasKnownArrayLength();
  }

  @Override
  public int getKnownArrayLength() {
    return getObjectState().getKnownArrayLength();
  }

  @Override
  public AbstractValue rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens lens, GraphLens codeLens) {
    return create(getObjectState().rewrittenWithLens(appView, lens, codeLens));
  }

  @Override
  public boolean hasObjectState() {
    return true;
  }

  @Override
  public ObjectState getObjectState() {
    return state;
  }

  @Override
  public String toString() {
    return "StatefulValue";
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StatefulObjectValue statefulObjectValue = (StatefulObjectValue) o;
    return state.equals(statefulObjectValue.state);
  }

  @Override
  public int hashCode() {
    return state.hashCode();
  }
}
