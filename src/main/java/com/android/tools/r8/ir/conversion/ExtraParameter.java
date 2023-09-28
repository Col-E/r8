// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.SingleConstValue;

public abstract class ExtraParameter {

  public abstract DexType getType(DexItemFactory dexItemFactory);

  public abstract TypeElement getTypeElement(AppView<?> appView, DexType argType);

  public abstract SingleConstValue getValue(AppView<?> appView);

  public abstract boolean isUnused();

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();
}
