// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

public class SingleNumberValue extends AbstractValue {

  private final long value;

  /** Intentionally package private, use {@link AbstractValueFactory} instead. */
  SingleNumberValue(long value) {
    this.value = value;
  }

  @Override
  public boolean isSingleNumberValue() {
    return true;
  }

  @Override
  public SingleNumberValue asSingleNumberValue() {
    return this;
  }

  public long getValue() {
    return value;
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
