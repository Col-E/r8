// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;

public class SingleStatelessFieldValue extends SingleFieldValue {

  /** Intentionally package private, use {@link AbstractValueFactory} instead. */
  SingleStatelessFieldValue(DexField field) {
    super(field);
  }

  @Override
  public ObjectState getObjectState() {
    return ObjectState.empty();
  }

  @Override
  public boolean hasObjectState() {
    return false;
  }

  @Override
  public String toString() {
    return "SingleStatelessFieldValue(" + field.toSourceString() + ")";
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SingleStatelessFieldValue singleFieldValue = (SingleStatelessFieldValue) o;
    return field == singleFieldValue.field;
  }

  @Override
  public int hashCode() {
    return field.hashCode();
  }
}
