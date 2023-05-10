// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class UnknownFieldSet extends AbstractFieldSet {

  private static final UnknownFieldSet INSTANCE = new UnknownFieldSet();

  private UnknownFieldSet() {}

  public static UnknownFieldSet getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean contains(DexClassAndField field) {
    return true;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public boolean isTop() {
    return true;
  }

  @Override
  public AbstractFieldSet fixupReadSetAfterParametersChanged(
      AppView<AppInfoWithLiveness> appView, ArgumentInfoCollection argumentInfoCollection) {
    return this;
  }

  @Override
  public AbstractFieldSet rewrittenWithLens(
      AppView<?> appView, GraphLens lens, GraphLens codeLens, PrunedItems prunedItems) {
    return this;
  }
}
