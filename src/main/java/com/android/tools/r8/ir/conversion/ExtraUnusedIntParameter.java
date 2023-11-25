// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;

public class ExtraUnusedIntParameter extends ExtraParameter {

  @Override
  public DexType getType(DexItemFactory dexItemFactory) {
    return dexItemFactory.intType;
  }

  @Override
  public TypeElement getTypeElement(AppView<?> appView, DexType argType) {
    return TypeElement.getInt();
  }

  @Override
  public SingleNumberValue getValue(AppView<?> appView) {
    return appView.abstractValueFactory().createZeroValue();
  }

  @Override
  public boolean isUnused() {
    return true;
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object obj) {
    return obj != null && getClass() == obj.getClass();
  }

  @Override
  public int hashCode() {
    return 0;
  }
}
