// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.graph.proto.RemovedArgumentInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class EmptyFieldSet extends AbstractFieldSet implements KnownFieldSet {

  private static final EmptyFieldSet INSTANCE = new EmptyFieldSet();

  private EmptyFieldSet() {}

  public static EmptyFieldSet getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isKnownFieldSet() {
    return true;
  }

  @Override
  public EmptyFieldSet asKnownFieldSet() {
    return this;
  }

  @Override
  public boolean contains(DexEncodedField field) {
    return false;
  }

  @Override
  public boolean contains(DexClassAndField field) {
    return false;
  }

  @Override
  public boolean isBottom() {
    return true;
  }

  @Override
  public AbstractFieldSet fixupReadSetAfterParametersChanged(
      AppView<AppInfoWithLiveness> appView, ArgumentInfoCollection argumentInfoCollection) {
    if (argumentInfoCollection.isEmpty()) {
      return this;
    }

    // Find the new field gets that are introduced as a result of constant parameter removal.
    ConcreteMutableFieldSet newReadSet = new ConcreteMutableFieldSet();
    argumentInfoCollection.forEach(
        (argumentIndex, argumentInfo) -> {
          if (argumentInfo.isRemovedArgumentInfo()) {
            RemovedArgumentInfo removedArgumentInfo = argumentInfo.asRemovedArgumentInfo();
            if (removedArgumentInfo.hasSingleValue()
                && removedArgumentInfo.getSingleValue().isSingleFieldValue()) {
              DexEncodedField definition =
                  removedArgumentInfo.getSingleValue().asSingleFieldValue().getField(appView);
              if (definition != null) {
                newReadSet.add(definition);
              } else {
                assert false;
              }
            }
          }
        });

    return newReadSet.isEmpty() ? this : newReadSet;
  }

  @Override
  public AbstractFieldSet rewrittenWithLens(
      AppView<?> appView, GraphLens lens, GraphLens codeLens, PrunedItems prunedItems) {
    return this;
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public int size() {
    return 0;
  }
}
