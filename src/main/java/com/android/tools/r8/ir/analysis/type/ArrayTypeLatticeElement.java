// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexType;
import com.google.common.base.Strings;

public class ArrayTypeLatticeElement extends TypeLatticeElement {
  final DexType elementType;
  final int nesting;

  ArrayTypeLatticeElement(DexType elementType, int nesting, boolean isNullable) {
    super(isNullable);
    this.elementType = elementType;
    this.nesting = nesting;
  }

  @Override
  TypeLatticeElement asNullable() {
    return isNullable() ? this : new ArrayTypeLatticeElement(elementType, nesting, true);
  }

  @Override
  public TypeLatticeElement arrayGet(AppInfoWithSubtyping appInfo) {
    if (nesting == 1) {
      return fromDexType(appInfo, elementType, true);
    }
    return new ArrayTypeLatticeElement(elementType, nesting - 1, true);
  }

  @Override
  public TypeLatticeElement checkCast(AppInfoWithSubtyping appInfo, DexType castType) {
    if (castType.getNumberOfLeadingSquareBrackets() == nesting) {
      DexType base = castType.toBaseType(appInfo.dexItemFactory);
      if (elementType.isSubtypeOf(base, appInfo)) {
        return this;
      }
    }
    return super.checkCast(appInfo, castType);
  }

  @Override
  public String toString() {
    return isNullableString() + elementType.toString() + Strings.repeat("[]", nesting);
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    ArrayTypeLatticeElement other = (ArrayTypeLatticeElement) o;
    return nesting == other.nesting && elementType == other.elementType;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + elementType.hashCode();
    result = 31 * result + nesting;
    return result;
  }
}
