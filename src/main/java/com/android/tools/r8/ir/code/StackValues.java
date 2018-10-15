// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import java.util.List;

/**
 * {@link StackValues} allow us to represent stack operations that produces two or more elements on
 * the stack while using the same logic for instructions.
 */
public class StackValues extends Value {

  private final int height;
  private final List<StackValue> stackValues;

  public StackValues(TypeLatticeElement typeLattice, int height, List<StackValue> stackValues) {
    super(Value.UNDEFINED_NUMBER, typeLattice, null);
    this.height = height;
    this.stackValues = stackValues;
    assert height >= 0;
    assert stackValues.size() >= 2;
  }

  public int getHeight() {
    return height;
  }

  public List<StackValue> getStackValues() {
    return stackValues;
  }

  @Override
  public boolean needsRegister() {
    return false;
  }

  @Override
  public void setNeedsRegister(boolean value) {
    assert !value;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    return String.format("s%d+%d", height, stackValues.size() - 1);
  }
}
