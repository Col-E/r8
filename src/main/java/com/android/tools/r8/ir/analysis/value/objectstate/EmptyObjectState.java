// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value.objectstate;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.function.BiConsumer;

public class EmptyObjectState extends ObjectState {

  private static final EmptyObjectState INSTANCE = new EmptyObjectState();

  private EmptyObjectState() {}

  public static EmptyObjectState getInstance() {
    return INSTANCE;
  }

  @Override
  public void forEachAbstractFieldValue(BiConsumer<DexField, AbstractValue> consumer) {
    // Intentionally empty.
  }

  @Override
  public AbstractValue getAbstractFieldValue(DexEncodedField field) {
    return UnknownValue.getInstance();
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public ObjectState rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLens lens, GraphLens codeLens) {
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
