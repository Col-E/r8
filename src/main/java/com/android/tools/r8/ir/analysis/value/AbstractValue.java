// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

public abstract class AbstractValue {

  /**
   * Returns true if this abstract value represents a single concrete value (i.e., the
   * concretization of this abstract value has size 1).
   */
  public boolean isSingleValue() {
    return isSingleEnumValue() || isSingleNumberValue() || isSingleStringValue();
  }

  public boolean isSingleEnumValue() {
    return false;
  }

  public SingleEnumValue asSingleEnumValue() {
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

  public boolean isUnknown() {
    return false;
  }

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();
}
