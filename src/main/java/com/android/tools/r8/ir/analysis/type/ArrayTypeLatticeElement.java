// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;

public class ArrayTypeLatticeElement extends ReferenceTypeLatticeElement {

  public ArrayTypeLatticeElement(DexType type, boolean isNullable) {
    super(type, isNullable);
    assert type.isArrayType();
  }

  public DexType getArrayType() {
    return type;
  }

  public int getNesting() {
    return type.getNumberOfLeadingSquareBrackets();
  }

  public DexType getArrayElementType(DexItemFactory factory) {
    return type.toArrayElementType(factory);
  }

  public DexType getArrayBaseType(DexItemFactory factory) {
    return type.toBaseType(factory);
  }

  @Override
  public ReferenceTypeLatticeElement getOrCreateDualLattice() {
    if (dual != null) {
      return dual;
    }
    synchronized (this) {
      if (dual == null) {
        ArrayTypeLatticeElement dual = new ArrayTypeLatticeElement(type, !isNullable());
        linkDualLattice(this, dual);
      }
    }
    return this.dual;
  }

  @Override
  public TypeLatticeElement asNullable() {
    return isNullable() ? this : getOrCreateDualLattice();
  }

  @Override
  public TypeLatticeElement asNonNullable() {
    return !isNullable() ? this : getOrCreateDualLattice();
  }

  @Override
  public boolean isBasedOnMissingClass(AppInfo appInfo) {
    return getArrayBaseType(appInfo.dexItemFactory).isMissingOrHasMissingSuperType(appInfo);
  }

  @Override
  public boolean isArrayType() {
    return true;
  }

  @Override
  public ArrayTypeLatticeElement asArrayTypeLatticeElement() {
    return this;
  }

  @Override
  public TypeLatticeElement arrayGet(AppInfo appInfo) {
    return fromDexType(getArrayElementType(appInfo.dexItemFactory), true, appInfo);
  }

  @Override
  public int hashCode() {
    return (isNullable() ? 1 : -1) * type.hashCode();
  }

}
