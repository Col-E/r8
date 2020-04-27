// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.DexField;
import java.util.Objects;

public class SingleStatefulFieldValue extends SingleFieldValue {

  private final ObjectState state;

  /** Intentionally package private, use {@link AbstractValueFactory} instead. */
  SingleStatefulFieldValue(DexField field, ObjectState state) {
    super(field);
    assert !state.isEmpty();
    this.state = state;
  }

  @Override
  public ObjectState getState() {
    return state;
  }

  @Override
  public String toString() {
    return "SingleStatefulFieldValue(" + field.toSourceString() + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (getClass() != o.getClass()) {
      return false;
    }
    SingleStatefulFieldValue singleFieldValue = (SingleStatefulFieldValue) o;
    return field == singleFieldValue.field && state.equals(singleFieldValue.state);
  }

  @Override
  public int hashCode() {
    return Objects.hash(field, state);
  }
}
