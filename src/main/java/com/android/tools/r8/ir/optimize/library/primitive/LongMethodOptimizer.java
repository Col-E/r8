// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library.primitive;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;

public class LongMethodOptimizer extends PrimitiveMethodOptimizer {

  LongMethodOptimizer(AppView<?> appView) {
    super(appView);
  }

  @Override
  DexMethod getBoxMethod() {
    return dexItemFactory.longMembers.valueOf;
  }

  @Override
  DexMethod getUnboxMethod() {
    return dexItemFactory.longMembers.longValue;
  }

  @Override
  boolean isMatchingSingleBoxedPrimitive(AbstractValue abstractValue) {
    return abstractValue.isSingleBoxedLong();
  }

  @Override
  public DexType getType() {
    return dexItemFactory.boxedLongType;
  }
}
