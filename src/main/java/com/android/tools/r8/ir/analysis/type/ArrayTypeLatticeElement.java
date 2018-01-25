// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;

public class ArrayTypeLatticeElement extends TypeLatticeElement {
  private final DexType arrayType;

  ArrayTypeLatticeElement(DexType arrayType, boolean isNullable) {
    super(isNullable);
    this.arrayType = arrayType;
  }

  public DexType getArrayType() {
    return arrayType;
  }

  public int getNesting() {
    return arrayType.getNumberOfLeadingSquareBrackets();
  }

  public DexType getArrayElementType(DexItemFactory factory) {
    return arrayType.toArrayElementType(factory);
  }

  public DexType getArrayBaseType(DexItemFactory factory) {
    return arrayType.toBaseType(factory);
  }

  @Override
  TypeLatticeElement asNullable() {
    return isNullable() ? this : new ArrayTypeLatticeElement(arrayType, true);
  }

  @Override
  public TypeLatticeElement asNonNullable() {
    return isNullable() ? new ArrayTypeLatticeElement(arrayType, false) : this;
  }

  @Override
  public boolean isArrayTypeLatticeElement() {
    return true;
  }

  @Override
  public ArrayTypeLatticeElement asArrayTypeLatticeElement() {
    return this;
  }

  @Override
  public TypeLatticeElement arrayGet(AppInfo appInfo) {
    return fromDexType(getArrayElementType(appInfo.dexItemFactory), true);
  }

  @Override
  public TypeLatticeElement checkCast(AppInfo appInfo, DexType castType) {
    if (castType.getNumberOfLeadingSquareBrackets() == getNesting()) {
      DexType base = castType.toBaseType(appInfo.dexItemFactory);
      if (getArrayBaseType(appInfo.dexItemFactory).isSubtypeOf(base, appInfo)) {
        return this;
      }
    }
    return super.checkCast(appInfo, castType);
  }

  @Override
  public String toString() {
    return isNullableString() + arrayType.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    ArrayTypeLatticeElement other = (ArrayTypeLatticeElement) o;
    return arrayType == other.arrayType;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + arrayType.hashCode();
    return result;
  }
}
