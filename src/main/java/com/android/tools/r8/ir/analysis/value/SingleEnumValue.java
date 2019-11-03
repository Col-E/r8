// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.DexField;

public class SingleEnumValue extends AbstractValue {

  private final DexField field;

  /** Intentionally package private, use {@link AbstractValueFactory} instead. */
  SingleEnumValue(DexField field) {
    this.field = field;
  }

  @Override
  public boolean isSingleValue() {
    return true;
  }

  @Override
  public boolean isSingleEnumValue() {
    return true;
  }

  @Override
  public SingleEnumValue asSingleEnumValue() {
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
}
