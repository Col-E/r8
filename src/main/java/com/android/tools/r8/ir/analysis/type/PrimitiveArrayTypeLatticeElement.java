// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.google.common.base.Strings;

public class PrimitiveArrayTypeLatticeElement extends TypeLatticeElement {
  final int nesting;

  PrimitiveArrayTypeLatticeElement(int nesting, boolean isNullable) {
    super(isNullable);
    this.nesting = nesting;
  }

  @Override
  TypeLatticeElement asNullable() {
    return isNullable() ? this : new PrimitiveArrayTypeLatticeElement(nesting, true);
  }

  @Override
  public TypeLatticeElement arrayGet(AppInfoWithSubtyping appInfo) {
    if (nesting == 1) {
      return PrimitiveTypeLatticeElement.getInstance();
    }
    return new PrimitiveArrayTypeLatticeElement(nesting - 1, true);
  }

  @Override
  public String toString() {
    return isNullableString() + "PRIMITIVE" + Strings.repeat("[]", nesting);
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    PrimitiveArrayTypeLatticeElement other = (PrimitiveArrayTypeLatticeElement) o;
    return nesting == other.nesting;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + nesting;
    return result;
  }
}
