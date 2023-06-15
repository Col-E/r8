// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.lens;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;

public class DefaultNonIdentityGraphLens extends NonIdentityGraphLens {

  public DefaultNonIdentityGraphLens(AppView<?> appView) {
    this(appView.dexItemFactory(), appView.graphLens());
  }

  public DefaultNonIdentityGraphLens(DexItemFactory dexItemFactory, GraphLens previousLens) {
    super(dexItemFactory, previousLens);
  }

  @Override
  public boolean isContextFreeForMethods(GraphLens codeLens) {
    if (this == codeLens) {
      return true;
    }
    return getPrevious().isContextFreeForMethods(codeLens);
  }

  // Class lookup APIs.

  @Override
  protected DexType getNextClassType(DexType type) {
    return type;
  }

  @Override
  public Iterable<DexType> getOriginalTypes(DexType type) {
    return getPrevious().getOriginalTypes(type);
  }

  @Override
  public DexType getPreviousClassType(DexType type) {
    return type;
  }

  // Field lookup APIs.

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    return previous;
  }

  @Override
  public DexField getPreviousFieldSignature(DexField field) {
    return field;
  }

  @Override
  public DexField getNextFieldSignature(DexField field) {
    return field;
  }

  // Method lookup APIs.

  @Override
  protected MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
    return previous;
  }

  @Override
  public DexMethod getPreviousMethodSignature(DexMethod method) {
    return method;
  }

  @Override
  public DexMethod getNextMethodSignature(DexMethod method) {
    return method;
  }

  // Prototype lookup APIs.

  @Override
  public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
      DexMethod method, GraphLens codeLens) {
    if (this == codeLens) {
      return RewrittenPrototypeDescription.none();
    }
    DexMethod previousMethodSignature = getPreviousMethodSignature(method);
    return getPrevious().lookupPrototypeChangesForMethodDefinition(previousMethodSignature);
  }
}
