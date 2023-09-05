// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;

public class ExtraConstantIntParameter extends ExtraParameter {

  private final long value;

  public ExtraConstantIntParameter(long value) {
    this.value = value;
  }

  @Override
  public DexType getType(DexItemFactory dexItemFactory) {
    return dexItemFactory.intType;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public TypeElement getTypeElement(AppView<?> appView, DexType argType) {
    assert argType == appView.dexItemFactory().intType;
    return TypeElement.getInt();
  }

  @Override
  public SingleNumberValue getValue(AppView<?> appView) {
    return appView.abstractValueFactory().createSingleNumberValue(value);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ExtraConstantIntParameter other = (ExtraConstantIntParameter) obj;
    return value == other.value;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(value);
  }
}
