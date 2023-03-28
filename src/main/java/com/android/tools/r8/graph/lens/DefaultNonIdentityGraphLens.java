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
  public boolean isContextFreeForMethods() {
    return getPrevious().isContextFreeForMethods();
  }

  // Class lookup APIs.

  @Override
  protected DexType internalDescribeLookupClassType(DexType previous) {
    return previous;
  }

  @Override
  public DexType getOriginalType(DexType type) {
    return getPrevious().getOriginalType(type);
  }

  @Override
  public Iterable<DexType> getOriginalTypes(DexType type) {
    return getPrevious().getOriginalTypes(type);
  }

  // Field lookup APIs.

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    return previous;
  }

  @Override
  public DexField getOriginalFieldSignature(DexField field) {
    return getPrevious().getOriginalFieldSignature(field);
  }

  @Override
  public DexField getRenamedFieldSignature(DexField originalField, GraphLens codeLens) {
    if (this == codeLens) {
      return originalField;
    }
    return getPrevious().getRenamedFieldSignature(originalField);
  }

  // Method lookup APIs.

  @Override
  protected MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context) {
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

  @Override
  public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens codeLens) {
    if (this == codeLens) {
      return originalMethod;
    }
    return getNextMethodSignature(getPrevious().getRenamedMethodSignature(originalMethod));
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
